package com.example.edps.infra.kafka.consumer;

import com.example.edps.domain.payment.event.PaymentCompletedEvent;
import com.example.edps.domain.payment.service.PaymentResultTxService;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.kafka.message.EventEnvelopeParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {
    private final PaymentResultTxService paymentResultTxService;
    private final EventEnvelopeParser eventEnvelopeParser;

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENT_SUCCEEDED, groupId = "payment-result")
    public void onSuccess(String value) {
        EventEnvelope<PaymentCompletedEvent> envelope
                = eventEnvelopeParser.parse(value, KafkaTopics.PAYMENT_EVENT_SUCCEEDED);
        PaymentCompletedEvent event = envelope.payload();

        paymentResultTxService.applySuccess(event, envelope.eventId());
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENT_FAILED, groupId = "payment-result")
    public void onFailed(String value) {
        EventEnvelope<PaymentCompletedEvent> envelope
                = eventEnvelopeParser.parse(value, KafkaTopics.PAYMENT_EVENT_FAILED);
        PaymentCompletedEvent event = envelope.payload();

        paymentResultTxService.applyFailure(event, envelope.eventId());
    }
}
