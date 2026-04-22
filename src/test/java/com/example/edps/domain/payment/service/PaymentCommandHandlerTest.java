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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

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
 * - Redis 락 획득 실패(이미 선점됨) → PG 호출 없음
 * - PG 성공        → SUCCESS 확정
 * - PG 비즈니스 오류 → FAILED 확정, 재시도 없음
 * - 일시적 오류    → Kafka 재시도를 위해 RuntimeException 재발생
 */
@ExtendWith(MockitoExtension.class)
class PaymentCommandHandlerTest {

    @Mock private PaymentTxService paymentTxService;
    @Mock private PaymentClient paymentClient;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock lock;

    private PaymentCommandHandler handler;

    private static final long PAYMENT_ID = 1L;
    private static final long ORDER_ID = 100L;
    private static final int AMOUNT = 10_000;

    @BeforeEach
    void setUp() {
        handler = new PaymentCommandHandler(paymentTxService, paymentClient, redissonClient);
        given(redissonClient.getLock("payment-claim-" + PAYMENT_ID)).willReturn(lock);
    }

    @Test
    @DisplayName("Redis 락 획득 실패 시 PG를 호출하지 않는다")
    void lock_acquisition_failure_skips_pg_call() throws InterruptedException {
        given(lock.tryLock(0, 30, TimeUnit.SECONDS)).willReturn(false);

        handler.process(makeEnvelope(PAYMENT_ID));

        then(paymentClient).shouldHaveNoInteractions();
        then(paymentTxService).should(never()).confirm(any(), any(), any(), any(), any(), any(), any(), any());
        then(paymentTxService).should(never()).handleTransientTx(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("PG 승인 성공 시 SUCCESS 상태로 결제를 확정한다")
    void pg_approval_success_confirms_payment_as_success() throws InterruptedException {
        given(lock.tryLock(0, 30, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(paymentClient.requestPayment(any(PaymentRequestedCommand.class)))
                .willReturn(new PgPaymentResponse("SUCCESS", "pg-tx-123", null));

        handler.process(makeEnvelope(PAYMENT_ID));

        ArgumentCaptor<PayStatus> statusCaptor = ArgumentCaptor.forClass(PayStatus.class);
        ArgumentCaptor<String> pgTxIdCaptor = ArgumentCaptor.forClass(String.class);
        then(paymentTxService).should().confirm(
                eq(makeEnvelope(PAYMENT_ID).payload()), any(), any(),
                statusCaptor.capture(), pgTxIdCaptor.capture(), any(), any(), any()
        );
        assertThat(statusCaptor.getValue()).isEqualTo(PayStatus.SUCCESS);
        assertThat(pgTxIdCaptor.getValue()).isEqualTo("pg-tx-123");
        then(lock).should().unlock();
    }

    @Test
    @DisplayName("PG 비즈니스 오류(4xx)는 재시도 없이 FAILED로 확정하고 예외를 전파하지 않는다")
    void pg_business_error_confirms_payment_as_failed_without_retry() throws InterruptedException {
        given(lock.tryLock(0, 30, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(paymentClient.requestPayment(any(PaymentRequestedCommand.class)))
                .willThrow(new PgBusinessException(400, "카드 한도 초과"));

        handler.process(makeEnvelope(PAYMENT_ID));

        ArgumentCaptor<PayStatus> statusCaptor = ArgumentCaptor.forClass(PayStatus.class);
        then(paymentTxService).should().confirm(
                eq(makeEnvelope(PAYMENT_ID).payload()), any(), any(),
                statusCaptor.capture(), isNull(), any(), any(), any()
        );
        then(paymentTxService).should(never()).handleTransientTx(any(), any(), any(), any(), any());
        assertThat(statusCaptor.getValue()).isEqualTo(PayStatus.FAILED);
        then(lock).should().unlock();
    }

    @Test
    @DisplayName("일시적 오류는 handleTransientTx를 호출하고 Kafka 재시도를 위해 Exception을 던진다")
    void transient_error_calls_handle_transient_and_rethrows_for_kafka_retry() throws InterruptedException {
        given(lock.tryLock(0, 30, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(paymentClient.requestPayment(any(PaymentRequestedCommand.class)))
                .willThrow(new RuntimeException("connection timeout"));

        assertThatThrownBy(() -> handler.process(makeEnvelope(PAYMENT_ID)))
                .isInstanceOf(RuntimeException.class);

        then(paymentTxService).should()
                .handleTransientTx(eq(makeEnvelope(PAYMENT_ID).payload()), any(), any(), any(), any());
        then(paymentTxService).should(never())
                .confirm(any(), any(), any(), any(), any(), any(), any(), any());
        then(lock).should().unlock();
    }

    private EventEnvelope<PaymentRequestedCommand> makeEnvelope(Long paymentId) {
        PaymentRequestedCommand cmd = new PaymentRequestedCommand(
                ORDER_ID, paymentId, "user-1", AMOUNT, PgScenario.SUCCESS);
        return EventEnvelope.of("trace-1", KafkaTopics.PAYMENT_COMMAND_REQUESTED, cmd);
    }
}
