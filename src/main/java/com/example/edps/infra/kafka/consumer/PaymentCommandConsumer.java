package com.example.edps.infra.kafka.consumer;

import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.domain.payment.service.PaymentCommandHandler;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.kafka.message.EventEnvelopeParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCommandConsumer {

    private final PaymentCommandHandler paymentCommandHandler;
    private final EventEnvelopeParser eventEnvelopeParser;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMMAND_REQUESTED,
            groupId = "edps-payment-worker"
    )
    public void onMessage(@Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
                          String value) {
        EventEnvelope<PaymentRequestedCommand> env =
                eventEnvelopeParser.parse(value, KafkaTopics.PAYMENT_COMMAND_REQUESTED);

        paymentCommandHandler.process(env);
    }
}
