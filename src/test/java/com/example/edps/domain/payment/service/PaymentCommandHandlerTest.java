package com.example.edps.domain.payment.service;

import com.example.edps.domain.order.enums.PgScenario;
import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.global.error.exception.PgBusinessException;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.handler.PaymentCommandHandler;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.pg.PaymentClient;
import com.example.edps.infra.pg.dto.PgPaymentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * PaymentCommandHandler 단위 테스트
 *
 * 검증 대상: "동일 Payment ID에 대해 단 하나의 워커만 PG 호출을 수행"
 * - claim 실패(이미 선점됨) → PG 호출 없음
 * - PG 성공        → SUCCESS 확정
 * - PG 비즈니스 오류 → FAILED 확정, 재시도 없음
 * - 일시적 오류    → Kafka 재시도를 위해 RuntimeException 재발생
 */
@ExtendWith(MockitoExtension.class)
class PaymentCommandHandlerTest {

    @Mock
    private PaymentTxService paymentTxService;

    @Mock
    private PaymentClient paymentClient;

    @InjectMocks
    private PaymentCommandHandler handler;

    private static final long PAYMENT_ID = 1L;
    private static final long ORDER_ID = 100L;
    private static final int AMOUNT = 10_000;

    @Test
    @DisplayName("이미 선점된 결제는 PG를 호출하지 않는다")
    void already_claimed_payment_skips_pg_call() {
        // given
        EventEnvelope<PaymentRequestedCommand> envelope = makeEnvelope(PAYMENT_ID);
        given(paymentTxService.claim(PAYMENT_ID)).willReturn(false);
        System.out.println("[테스트] claim 실패 시나리오 - paymentId: " + PAYMENT_ID);

        // when
        handler.process(envelope);
        System.out.println("[결과] handler.process() 완료 - PG 호출 없어야 함");

        // then
        then(paymentClient).shouldHaveNoInteractions();
        // [수정] confirm + handleTransientTx 둘 다 미호출 명시적 검증
        then(paymentTxService).should(never())
                .confirm(any(), any(), any(), any(), any(), any(), any(), any());
        then(paymentTxService).should(never())
                .handleTransientTx(any(), any(), any(), any(), any());
        System.out.println("[검증] ✅ PG 미호출, confirm 미호출, handleTransientTx 미호출 확인");
    }

    @Test
    @DisplayName("PG 승인 성공 시 SUCCESS 상태로 결제를 확정한다")
    void pg_approval_success_confirms_payment_as_success() {
        // given
        EventEnvelope<PaymentRequestedCommand> envelope = makeEnvelope(PAYMENT_ID);
        given(paymentTxService.claim(PAYMENT_ID)).willReturn(true);
        given(paymentClient.requestPayment(eq(PAYMENT_ID), anyInt(), any()))
                .willReturn(new PgPaymentResponse("SUCCESS", "pg-tx-123", null));
        System.out.println("[테스트] PG 승인 성공 시나리오 - paymentId: " + PAYMENT_ID);

        // when
        handler.process(envelope);
        System.out.println("[결과] handler.process() 완료");

        // then - [수정] 핵심 값(status, pgTxId)만 Captor로 검증
        ArgumentCaptor<PayStatus> statusCaptor = ArgumentCaptor.forClass(PayStatus.class);
        ArgumentCaptor<String> pgTxIdCaptor = ArgumentCaptor.forClass(String.class);
        then(paymentTxService).should().confirm(
                eq(envelope.payload()), any(), any(),
                statusCaptor.capture(), pgTxIdCaptor.capture(), any(), any(), any()
        );
        assertThat(statusCaptor.getValue()).isEqualTo(PayStatus.SUCCESS);
        assertThat(pgTxIdCaptor.getValue()).isEqualTo("pg-tx-123");
        System.out.println("[검증] ✅ 상태: " + statusCaptor.getValue()
                + ", pgTxId: " + pgTxIdCaptor.getValue());
    }

    @Test
    @DisplayName("PG 비즈니스 오류(4xx)는 재시도 없이 FAILED로 확정하고 예외를 전파하지 않는다")
    void pg_business_error_confirms_payment_as_failed_without_retry() {
        // given
        EventEnvelope<PaymentRequestedCommand> envelope = makeEnvelope(PAYMENT_ID);
        given(paymentTxService.claim(PAYMENT_ID)).willReturn(true);
        given(paymentClient.requestPayment(eq(PAYMENT_ID), anyInt(), any()))
                .willThrow(new PgBusinessException(400, "카드 한도 초과"));
        System.out.println("[테스트] PG 비즈니스 오류(4xx) 시나리오 - paymentId: " + PAYMENT_ID);

        // when
        handler.process(envelope);
        System.out.println("[결과] handler.process() 완료 - 예외 전파 없음");

        // then
        ArgumentCaptor<PayStatus> statusCaptor = ArgumentCaptor.forClass(PayStatus.class);
        then(paymentTxService).should().confirm(
                eq(envelope.payload()), any(), any(),
                statusCaptor.capture(), isNull(), any(), any(), any()
        );
        then(paymentTxService).should(never())
                .handleTransientTx(any(), any(), any(), any(), any());
        assertThat(statusCaptor.getValue()).isEqualTo(PayStatus.FAILED);
        System.out.println("[검증] ✅ 상태: " + statusCaptor.getValue()
                + ", handleTransientTx 미호출 확인");
    }

    @Test
    @DisplayName("일시적 오류는 handleTransientTx를 호출하고 Kafka 재시도를 위해 Exception을 던진다")
    void transient_error_calls_handle_transient_and_rethrows_for_kafka_retry() {
        // given
        EventEnvelope<PaymentRequestedCommand> envelope = makeEnvelope(PAYMENT_ID);
        given(paymentTxService.claim(PAYMENT_ID)).willReturn(true);
        given(paymentClient.requestPayment(eq(PAYMENT_ID), anyInt(), any()))
                .willThrow(new RuntimeException("connection timeout"));
        System.out.println("[테스트] 일시적 오류(타임아웃) 시나리오 - paymentId: " + PAYMENT_ID);

        // when & then
        assertThatThrownBy(() -> handler.process(envelope))
                .isInstanceOf(RuntimeException.class);
        System.out.println("[결과] ✅ RuntimeException 재발생 확인 - Kafka 재시도 트리거");

        then(paymentTxService).should()
                .handleTransientTx(eq(envelope.payload()), any(), any(), any(), any());
        then(paymentTxService).should(never())
                .confirm(any(), any(), any(), any(), any(), any(), any(), any());
        System.out.println("[검증] ✅ handleTransientTx 호출, confirm 미호출 확인");
    }

    private EventEnvelope<PaymentRequestedCommand> makeEnvelope(Long paymentId) {
        PaymentRequestedCommand cmd = new PaymentRequestedCommand(
                ORDER_ID, paymentId, "user-1", AMOUNT, PgScenario.SUCCESS);
        return EventEnvelope.of("trace-1", KafkaTopics.PAYMENT_COMMAND_REQUESTED, cmd);
    }
}