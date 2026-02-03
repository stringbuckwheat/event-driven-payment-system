package com.example.edps.infra.kafka.config;

import com.example.edps.global.error.exception.PgBusinessException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaRetryConfig {
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        // 2초 간격 5회 재시도
        DefaultErrorHandler h = new DefaultErrorHandler(new FixedBackOff(2000L, 5L));

        // 비즈니스 실패는 재시도 X
        h.addNotRetryableExceptions(PgBusinessException.class);

        return h;
    }
}
