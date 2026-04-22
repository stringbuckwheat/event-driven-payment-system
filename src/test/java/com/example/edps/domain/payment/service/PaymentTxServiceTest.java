package com.example.edps.domain.payment.service;

import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.payment.entity.Payment;
import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.domain.payment.repository.PaymentLogRepository;
import com.example.edps.domain.payment.repository.PaymentRepository;
import com.example.edps.domain.order.enums.PgScenario;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.BusinessException;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.outbox.service.OutboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * PaymentTxService 단위 테스트
 *
 * 검증 대상:
 * - confirm(): 이미 확정된 결제는 skip, 토픽 라우팅, Payment not found 예외
 * - handleTransientTx(): maxAttempts 미만이면 Kafka 재전송 유도, maxAttempts 이상이면 FAILED 확정
 */
@ExtendWith(MockitoExtension.class)
class PaymentTxServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentLogRepository paymentLogRepository;
    @Mock private OutboxService outboxService;

    @InjectMocks private PaymentTxService paymentTxService;

    private static final long PAYMENT_ID = 1L;
    private static final long ORDER_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 17, 12, 0);

    // ===== confirm =====

    @Test
    @DisplayName("READY 상태 결제를 SUCCESS로 확정하면 succeeded 토픽으로 Outbox가 저장된다")
    void confirm_success_routes_to_succeeded_topic() {
        Payment payment = makePayment(PayStatus.READY);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
        given(paymentLogRepository.countByPaymentId(PAYMENT_ID)).willReturn(0L);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);

        paymentTxService.confirm(makeCmd(), "trace-1", "evt-1",
                PayStatus.SUCCESS, "pg-tx-001", null, NOW, NOW.plusSeconds(1));

        then(outboxService).should().save(topicCaptor.capture(), any(), any());
        assertThat(topicCaptor.getValue()).isEqualTo(KafkaTopics.PAYMENT_EVENT_SUCCEEDED);
        assertThat(payment.getStatus()).isEqualTo(PayStatus.SUCCESS);
        assertThat(payment.getPgTxId()).isEqualTo("pg-tx-001");
    }

    @Test
    @DisplayName("READY 상태 결제를 FAILED로 확정하면 failed 토픽으로 Outbox가 저장된다")
    void confirm_failure_routes_to_failed_topic() {
        Payment payment = makePayment(PayStatus.READY);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
        given(paymentLogRepository.countByPaymentId(PAYMENT_ID)).willReturn(0L);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);

        paymentTxService.confirm(makeCmd(), "trace-1", "evt-1",
                PayStatus.FAILED, null, "카드 한도 초과", NOW, NOW.plusSeconds(1));

        then(outboxService).should().save(topicCaptor.capture(), any(), any());
        assertThat(topicCaptor.getValue()).isEqualTo(KafkaTopics.PAYMENT_EVENT_FAILED);
        assertThat(payment.getStatus()).isEqualTo(PayStatus.FAILED);
    }

    @Test
    @DisplayName("이미 확정된 결제는 confirm()이 아무것도 하지 않는다")
    void confirm_skips_when_payment_is_already_finalized() {
        Payment payment = makePayment(PayStatus.SUCCESS);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

        paymentTxService.confirm(makeCmd(), "trace-1", "evt-1",
                PayStatus.SUCCESS, "pg-tx-001", null, NOW, NOW);

        then(outboxService).shouldHaveNoInteractions();
        then(paymentLogRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("confirm() 시 결제가 없으면 PAYMENT_NOT_FOUND 예외")
    void confirm_throws_business_exception_when_payment_not_found() {
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentTxService.confirm(makeCmd(), "trace-1", "evt-1",
                        PayStatus.SUCCESS, null, null, NOW, NOW))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorType())
                        .isEqualTo(ErrorType.PAYMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("attemptNo는 기존 PaymentLog 수 + 1이다")
    void confirm_attempt_no_is_existing_log_count_plus_one() {
        Payment payment = makePayment(PayStatus.READY);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
        given(paymentLogRepository.countByPaymentId(PAYMENT_ID)).willReturn(2L); // 이미 2번 시도

        paymentTxService.confirm(makeCmd(), "trace-1", "evt-1",
                PayStatus.SUCCESS, "pg-tx-001", null, NOW, NOW);

        // PaymentLog가 payment에 추가되어야 함 (attemptNo=3)
        assertThat(payment.getPaymentLogs()).hasSize(1);
        assertThat(payment.getPaymentLogs().get(0).getAttemptNo()).isEqualTo(3);
    }

    // ===== handleTransientTx =====

    @Test
    @DisplayName("maxAttempts(5) 미만이면 FAILED 로그를 남기고 Kafka 재전송을 유도한다")
    void transient_logs_and_allows_retry_when_under_max_attempts() {
        Payment payment = makePayment(PayStatus.READY);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
        given(paymentLogRepository.countByPaymentId(PAYMENT_ID)).willReturn(1L); // 2번째 시도

        paymentTxService.handleTransientTx(makeCmd(), "trace-1", "evt-1",
                NOW, new RuntimeException("connection timeout"));

        then(outboxService).shouldHaveNoInteractions();
        assertThat(payment.getPaymentLogs()).hasSize(1);
        assertThat(payment.getPaymentLogs().get(0).getFailureReason())
                .startsWith("TRANSIENT:");
        assertThat(payment.getPaymentLogs().get(0).getStatus()).isEqualTo(PayStatus.FAILED);
    }

    @Test
    @DisplayName("5번째 시도(maxAttempts 도달)에서는 FAILED로 확정하고 failed 토픽에 Outbox를 저장한다")
    void transient_exhausts_and_fails_payment_at_max_attempts() {
        Payment payment = makePayment(PayStatus.READY);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
        given(paymentLogRepository.countByPaymentId(PAYMENT_ID)).willReturn(4L); // 5번째 시도

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);

        paymentTxService.handleTransientTx(makeCmd(), "trace-1", "evt-1",
                NOW, new RuntimeException("DB timeout"));

        then(outboxService).should().save(topicCaptor.capture(), any(), any());
        assertThat(topicCaptor.getValue()).isEqualTo(KafkaTopics.PAYMENT_EVENT_FAILED);
        assertThat(payment.getStatus()).isEqualTo(PayStatus.FAILED);
    }

    @Test
    @DisplayName("이미 확정된 결제는 handleTransientTx가 아무것도 하지 않는다")
    void transient_skips_when_payment_is_already_finalized() {
        Payment payment = makePayment(PayStatus.SUCCESS);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

        paymentTxService.handleTransientTx(makeCmd(), "trace-1", "evt-1",
                NOW, new RuntimeException("timeout"));

        then(outboxService).shouldHaveNoInteractions();
        then(paymentLogRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("handleTransientTx() 시 결제가 없으면 PAYMENT_NOT_FOUND 예외")
    void transient_throws_business_exception_when_payment_not_found() {
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentTxService.handleTransientTx(makeCmd(), "trace-1", "evt-1",
                        NOW, new RuntimeException("timeout")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorType())
                        .isEqualTo(ErrorType.PAYMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("예외 메시지가 null이면 클래스 이름을 failureReason에 기록한다")
    void transient_uses_class_name_when_exception_message_is_null() {
        Payment payment = makePayment(PayStatus.READY);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
        given(paymentLogRepository.countByPaymentId(PAYMENT_ID)).willReturn(0L);

        paymentTxService.handleTransientTx(makeCmd(), "trace-1", "evt-1",
                NOW, new NullPointerException()); // message=null

        assertThat(payment.getPaymentLogs().get(0).getFailureReason())
                .contains("NullPointerException");
    }

    @Test
    @DisplayName("300자 초과 예외 메시지는 300자로 잘린다")
    void transient_truncates_exception_message_longer_than_300_chars() {
        Payment payment = makePayment(PayStatus.READY);
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
        given(paymentLogRepository.countByPaymentId(PAYMENT_ID)).willReturn(0L);

        String longMessage = "x".repeat(500);
        paymentTxService.handleTransientTx(makeCmd(), "trace-1", "evt-1",
                NOW, new RuntimeException(longMessage));

        String reason = payment.getPaymentLogs().get(0).getFailureReason();
        assertThat(reason.length()).isLessThanOrEqualTo("TRANSIENT: ".length() + 300);
    }

    // ===== helpers =====

    private Payment makePayment(PayStatus status) {
        Order order = Order.create("user-1", Collections.emptyList(), 10_000);
        Payment payment = new Payment(order);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        ReflectionTestUtils.setField(payment, "status", status);
        return payment;
    }

    private PaymentRequestedCommand makeCmd() {
        return new PaymentRequestedCommand(ORDER_ID, PAYMENT_ID, "user-1", 10_000, PgScenario.SUCCESS);
    }
}
