package com.example.edps.infra.kafka.consumer;

import com.example.edps.infra.kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentCommandDlqConsumer {
    @KafkaListener(topics = KafkaTopics.PAYMENT_COMMAND_REQUESTED_DLQ, groupId = "payment-dlq")
    public void dlq(String value) {
        log.error("[DLQ] payment.command.requested -> DLQ payload={}", value);
        // TODO Webhook 알람? 테이블 적재?
    }
}
