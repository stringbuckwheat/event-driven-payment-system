package com.example.edps.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class ObjectMapperConfig {
    @Bean
    public ObjectMapper kafkaObjectMapper() {
        return new ObjectMapper();
    }
}
