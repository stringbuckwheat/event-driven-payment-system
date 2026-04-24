package com.example.edps.infra.kafka.consumer;

import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.event.PaymentCompletedEvent;
import com.example.edps.domain.payment.service.PaymentResultTxService;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.BusinessException;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.kafka.message.EventEnvelopeParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * PaymentResultConsumer 단위 테스트
 * 멱등성(중복 이벤트 스킵)은 PaymentResultTxService 테스트에서 검증
 */
@ExtendWith(MockitoExtension.class)
class PaymentResultConsumerTest {

    @Mock
    private PaymentResultTxService paymentResultTxService;
    @Mock
    private EventEnvelopeParser eventEnvelopeParser;
    @InjectMocks
    private PaymentResultConsumer consumer;

    private static final String SUCCESS_TOPIC = KafkaTopics.PAYMENT_EVENT_SUCCEEDED;
    private static final String FAILED_TOPIC = KafkaTopics.PAYMENT_EVENT_FAILED;
    private static final String EVENT_ID = "fixed-event-id";

    private static final EventEnvelope<PaymentCompletedEvent> SUCCESS_ENVELOPE =
            new EventEnvelope<>(EVENT_ID, "trace-1", Instant.parse("2024-01-01T00:00:00Z"),
                    SUCCESS_TOPIC, successEvent());

    private static final EventEnvelope<PaymentCompletedEvent> FAILED_ENVELOPE =
            new EventEnvelope<>(EVENT_ID, "trace-1", Instant.parse("2024-01-01T00:00:00Z"),
                    FAILED_TOPIC, failedEvent());

    // ===== onSuccess =====

    @Test
    @DisplayName("성공 이벤트 수신 시 SUCCESS_TOPIC으로 파싱 후 applySuccess를 호출한다")
    void onSuccess_parses_with_correct_topic_and_calls_apply_success() {
        willReturn(SUCCESS_ENVELOPE).given(eventEnvelopeParser)
                .parse(eq("success-json"), eq(SUCCESS_TOPIC), eq(PaymentCompletedEvent.class));

        consumer.onSuccess("success-json");

        then(paymentResultTxService).should().applySuccess(SUCCESS_ENVELOPE.payload(), EVENT_ID);
        then(paymentResultTxService).should(never()).applyFailure(any(), any());
    }

    @Test
    @DisplayName("onSuccess는 envelope의 eventId를 applySuccess에 전달한다")
    void onSuccess_passes_event_id_from_envelope() {
        willReturn(SUCCESS_ENVELOPE).given(eventEnvelopeParser).parse(any(), any(), any());

        consumer.onSuccess("any-json");

        then(paymentResultTxService).should().applySuccess(any(), eq(EVENT_ID));
    }

    @Test
    @DisplayName("onSuccess 파싱 실패 시 RuntimeException을 전파해 Kafka retry를 유도한다")
    void onSuccess_parse_failure_propagates_exception() {
        willThrow(new RuntimeException(SUCCESS_TOPIC + " parse 실패"))
                .given(eventEnvelopeParser).parse(any(), any(), any());

        assertThatThrownBy(() -> consumer.onSuccess("{malformed}"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("parse 실패");

        then(paymentResultTxService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("onSuccess 처리 중 주문을 찾지 못하면 BusinessException을 전파한다")
    void onSuccess_order_not_found_propagates_business_exception() {
        willReturn(SUCCESS_ENVELOPE).given(eventEnvelopeParser).parse(any(), any(), any());
        willThrow(new BusinessException(ErrorType.ORDER_NOT_FOUND, "orderId=99"))
                .given(paymentResultTxService).applySuccess(any(), any());

        assertThatThrownBy(() -> consumer.onSuccess("any-json"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("onSuccess 처리 중 RuntimeException 발생 시 전파해 Kafka retry를 유도한다")
    void onSuccess_runtime_exception_propagates_for_kafka_retry() {
        willReturn(SUCCESS_ENVELOPE).given(eventEnvelopeParser).parse(any(), any(), any());
        willThrow(new RuntimeException("DB connection failed"))
                .given(paymentResultTxService).applySuccess(any(), any());

        assertThatThrownBy(() -> consumer.onSuccess("any-json"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB connection failed");
    }

    // ===== onFailed =====

    @Test
    @DisplayName("실패 이벤트 수신 시 FAILED_TOPIC으로 파싱 후 applyFailure를 호출한다")
    void onFailed_parses_with_correct_topic_and_calls_apply_failure() {
        willReturn(FAILED_ENVELOPE).given(eventEnvelopeParser)
                .parse(eq("failed-json"), eq(FAILED_TOPIC), eq(PaymentCompletedEvent.class));

        consumer.onFailed("failed-json");

        then(paymentResultTxService).should().applyFailure(FAILED_ENVELOPE.payload(), EVENT_ID);
        then(paymentResultTxService).should(never()).applySuccess(any(), any());
    }

    @Test
    @DisplayName("onFailed는 envelope의 eventId를 applyFailure에 전달한다")
    void onFailed_passes_event_id_from_envelope() {
        willReturn(FAILED_ENVELOPE).given(eventEnvelopeParser).parse(any(), any(), any());

        consumer.onFailed("any-json");

        then(paymentResultTxService).should().applyFailure(any(), eq(EVENT_ID));
    }

    @Test
    @DisplayName("onFailed 파싱 실패 시 RuntimeException을 전파해 Kafka retry를 유도한다")
    void onFailed_parse_failure_propagates_exception() {
        willThrow(new RuntimeException(FAILED_TOPIC + " parse 실패"))
                .given(eventEnvelopeParser).parse(any(), any(), any());

        assertThatThrownBy(() -> consumer.onFailed("{malformed}"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("parse 실패");

        then(paymentResultTxService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("onFailed 처리 중 주문을 찾지 못하면 BusinessException을 전파한다")
    void onFailed_order_not_found_propagates_business_exception() {
        willReturn(FAILED_ENVELOPE).given(eventEnvelopeParser).parse(any(), any(), any());
        willThrow(new BusinessException(ErrorType.ORDER_NOT_FOUND, "orderId=99"))
                .given(paymentResultTxService).applyFailure(any(), any());

        assertThatThrownBy(() -> consumer.onFailed("any-json"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("onFailed 처리 중 RuntimeException 발생 시 전파해 Kafka retry를 유도한다")
    void onFailed_runtime_exception_propagates_for_kafka_retry() {
        willReturn(FAILED_ENVELOPE).given(eventEnvelopeParser).parse(any(), any(), any());
        willThrow(new RuntimeException("DB connection failed"))
                .given(paymentResultTxService).applyFailure(any(), any());

        assertThatThrownBy(() -> consumer.onFailed("any-json"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB connection failed");
    }

    // ===== helpers =====

    private static PaymentCompletedEvent successEvent() {
        return PaymentCompletedEvent.builder()
                .orderId(100L).paymentId(1L)
                .status(PayStatus.SUCCESS).pgTxId("pg-tx-001")
                .requestedAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .respondedAt(LocalDateTime.of(2024, 1, 1, 0, 0, 1))
                .attemptNo(1).build();
    }

    private static PaymentCompletedEvent failedEvent() {
        return PaymentCompletedEvent.builder()
                .orderId(100L).paymentId(1L)
                .status(PayStatus.FAILED).failureReason("카드 한도 초과")
                .requestedAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .respondedAt(LocalDateTime.of(2024, 1, 1, 0, 0, 1))
                .attemptNo(1).build();
    }
}
