package com.example.edps.infra.kafka.consumer;

import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.handler.PaymentCommandHandler;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.kafka.message.EventEnvelopeParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.example.edps.infra.kafka.KafkaTopics.PAYMENT_COMMAND_REQUESTED;

/* 결제 요청 커맨드 수신 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCommandConsumer {

    private final PaymentCommandHandler paymentCommandHandler;
    private final EventEnvelopeParser eventEnvelopeParser;

    /**
     * 결제 요청 커맨드 수신
     * Outbox -> Debezium -> Kafka로 발행된 결제 커맨드 수신해 결제 처리
     *
     * @param value
     */
    @KafkaListener(
            topics = PAYMENT_COMMAND_REQUESTED,
            groupId = "edps-payment-worker"
    )
    public void onMessage(String value) {
        EventEnvelope<PaymentRequestedCommand> env =
                eventEnvelopeParser.parse(value, PAYMENT_COMMAND_REQUESTED, PaymentRequestedCommand.class);

        paymentCommandHandler.process(env);
    }
}
