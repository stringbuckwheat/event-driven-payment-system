package com.example.edps.infra.kafka.consumer;

import com.example.edps.infra.kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentCommandDlqConsumer {
    @KafkaListener(topics = KafkaTopics.PAYMENT_COMMAND_REQUESTED_DLQ, groupId = "payment-dlq")
    public void dlq(String value, @Header(value = KafkaHeaders.EXCEPTION_CAUSE_FQCN, required = false) String exceptionClass) {
        log.error("[DLQ] cause={}, payload={}", exceptionClass, value);
        // TODO Webhook 알람? 테이블 적재?
    }
}
