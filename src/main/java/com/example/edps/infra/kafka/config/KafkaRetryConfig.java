package com.example.edps.infra.kafka.config;

import com.example.edps.global.error.exception.PgBusinessException;
import com.example.edps.infra.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaRetryConfig {
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (r, e) -> new TopicPartition(
                                KafkaTopics.PAYMENT_COMMAND_REQUESTED_DLQ,
                                r.partition()
                        )
                );

        // 2초 간격 5회 재시도
        FixedBackOff fixedBackOff = new FixedBackOff(2000L, 5L);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, fixedBackOff);

        // 비즈니스 실패는 재시도 X
        handler.addNotRetryableExceptions(PgBusinessException.class);

        // 재시도마다 예외 로깅
        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.error("[Kafka Retry] attempt={}/{}, topic={}, cause={}",
                        deliveryAttempt, fixedBackOff.getMaxAttempts() + 1,
                        record.topic(), ex.getMessage()));

        return handler;
    }
}
