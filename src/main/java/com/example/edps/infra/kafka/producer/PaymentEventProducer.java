package com.example.edps.infra.kafka.producer;

import com.example.edps.domain.payment.event.EventEnvelope;
import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.infra.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentRequested(String traceId, PaymentRequestedCommand cmd) {
        EventEnvelope<PaymentRequestedCommand> event
                = EventEnvelope.of(traceId, KafkaTopics.PAYMENT_COMMAND_REQUESTED, cmd);

        // key: 결제 ID
        String key = String.valueOf(cmd.paymentId());

        // topic, key, data
        kafkaTemplate.send(
                KafkaTopics.PAYMENT_COMMAND_REQUESTED,
                key,
                event
        );
    }
}
