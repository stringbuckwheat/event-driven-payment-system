package com.example.edps.infra.kafka.consumer;

import com.example.edps.domain.order.enums.PgScenario;
import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.handler.PaymentCommandHandler;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.kafka.message.EventEnvelopeParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * PaymentCommandConsumer 단위 테스트
 * 분산락/트랜잭션 로직은 PaymentCommandHandler 테스트에서 검증
 */
@ExtendWith(MockitoExtension.class)
class PaymentCommandConsumerTest {

    @Mock
    private PaymentCommandHandler paymentCommandHandler;
    @Mock
    private EventEnvelopeParser eventEnvelopeParser;
    @InjectMocks
    private PaymentCommandConsumer consumer;

    private static final String TOPIC = KafkaTopics.PAYMENT_COMMAND_REQUESTED;
    private static final PaymentRequestedCommand CMD =
            new PaymentRequestedCommand(100L, 1L, "user-1", 10_000, PgScenario.SUCCESS);
    private static final EventEnvelope<PaymentRequestedCommand> ENVELOPE =
            new EventEnvelope<>("fixed-event-id", "trace-1", Instant.now(), TOPIC, CMD);

    @Test
    @DisplayName("유효한 메시지 수신 시 파싱 후 핸들러를 정확한 envelope로 호출한다")
    void valid_message_parses_and_delegates_to_handler() {
        willReturn(ENVELOPE).given(eventEnvelopeParser).parse(eq("valid-json"), eq(TOPIC), eq(PaymentRequestedCommand.class));

        consumer.onMessage("valid-json");

        then(paymentCommandHandler).should().process(ENVELOPE);
    }

    @Test
    @DisplayName("파싱 실패 시 RuntimeException을 전파해 retry 유도")
    void parse_failure_propagates_exception_for_kafka_retry() {
        willThrow(new RuntimeException(TOPIC + " parse 실패"))
                .given(eventEnvelopeParser).parse(any(), any(), any());

        assertThatThrownBy(() -> consumer.onMessage("{malformed}"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("parse 실패");

        then(paymentCommandHandler).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("null 메시지 수신 시 파싱 예외를 전파 (핸들러 호출X)")
    void null_message_propagates_parse_exception() {
        willThrow(new RuntimeException(TOPIC + " parse 실패"))
                .given(eventEnvelopeParser).parse(isNull(), any(), any());

        assertThatThrownBy(() -> consumer.onMessage(null))
                .isInstanceOf(RuntimeException.class);

        then(paymentCommandHandler).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("핸들러에서 exception 발생 시 예외를 전파해 Kafka retry 유도")
    void handler_runtime_exception_propagates_for_kafka_retry() {
        willReturn(ENVELOPE).given(eventEnvelopeParser).parse(any(), any(), any());
        willThrow(new RuntimeException("connection timeout"))
                .given(paymentCommandHandler).process(any());

        assertThatThrownBy(() -> consumer.onMessage("valid-json"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("connection timeout");
    }

    @Test
    @DisplayName("핸들러 정상 처리 시 예외 전파X")
    void handler_normal_return_does_not_propagate_exception() {
        willReturn(ENVELOPE).given(eventEnvelopeParser).parse(any(), any(), any());

        consumer.onMessage("valid-json");

        then(paymentCommandHandler).should().process(ENVELOPE);
    }
}
