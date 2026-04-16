package com.example.edps.infra.kafka.message;

import com.example.edps.infra.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class EventEnvelopeParser {
    private final ObjectMapper objectMapper;

    public <T> EventEnvelope<T> parse(String json, String topic) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException(topic + " parse 실패", e);
        }
    }
}
