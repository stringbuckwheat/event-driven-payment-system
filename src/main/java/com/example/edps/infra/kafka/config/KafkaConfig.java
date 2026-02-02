package com.example.edps.infra.kafka.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class KafkaConfig {
    @Bean
    public ObjectMapper kafkaObjectMapper() {
        return new ObjectMapper();
    }
}
