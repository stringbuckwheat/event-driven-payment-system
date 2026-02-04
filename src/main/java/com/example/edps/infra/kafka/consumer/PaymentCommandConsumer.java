package com.example.edps.infra.kafka.consumer;

import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.domain.payment.service.PaymentCommandHandler;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.message.EventEnvelope;
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

    private final ObjectMapper objectMapper;
    private final PaymentCommandHandler paymentCommandHandler;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMMAND_REQUESTED,
            groupId = "edps-payment-worker"
    )
    public void onMessage(@Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
                          String value) {
        log.info("========================= PaymentCommandConsumer ========================");

        EventEnvelope<PaymentRequestedCommand> env = parse(value);
        PaymentRequestedCommand cmd = env.payload();

        log.info("수신확인 topic={}, key={}, paymentId={}, orderId={}, eventId={}",
                KafkaTopics.PAYMENT_COMMAND_REQUESTED, key, cmd.paymentId(), cmd.orderId(), env.eventId());

        paymentCommandHandler.process(env);
    }

    private EventEnvelope<PaymentRequestedCommand> parse(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("PAYMENT_REQUESTED parse 실패", e);
        }
    }
}
