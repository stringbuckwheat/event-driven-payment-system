package com.example.edps.infra.kafka.config.consumer;

import com.example.edps.infra.kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentCommandConsumer {

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMMAND_REQUESTED,
            groupId = "edps-1"
    )
    public void onMessage(String key, String value) {
        log.info("수신확인! topic={}, key={}, value={}",
                KafkaTopics.PAYMENT_COMMAND_REQUESTED, key, value);
    }
}