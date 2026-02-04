package com.example.edps.infra.kafka.consumer;

import com.example.edps.domain.payment.event.PaymentCompletedEvent;
import com.example.edps.domain.payment.service.PaymentResultTxService;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.message.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {
    private final ObjectMapper objectMapper;
    private final PaymentResultTxService paymentResultTxService;

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENT_SUCCEEDED, groupId = "payment-result")
    public void onSuccess(String value) {
        log.info("========================= PaymentResultConsumer - SUCCESS ========================");
        EventEnvelope<PaymentCompletedEvent> envelope = parse(value);
        PaymentCompletedEvent event = envelope.payload();

        paymentResultTxService.applySuccess(event, envelope.eventId());
        log.info("payment result applied: COMPLETED orderId={}, paymentId={}, eventId={}",
                event.orderId(), event.paymentId(), envelope.eventId());
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENT_FAILED, groupId = "payment-result")
    public void onFailed(String value) {
        log.info("========================= PaymentResultConsumer - FAILED ========================");
        EventEnvelope<PaymentCompletedEvent> envelope = parse(value);
        PaymentCompletedEvent event = envelope.payload();

        paymentResultTxService.applyFailure(event, envelope.eventId());

        log.info("payment result applied: FAILED orderId={}, paymentId={}, reason={}, eventId={}",
                event.orderId(), event.paymentId(), event.failureReason(), envelope.eventId());
    }

    private EventEnvelope<PaymentCompletedEvent> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("PAYMENT_REQUESTED parse 실패", e);
        }
    }
}
