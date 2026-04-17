package com.example.edps.infra.kafka.message;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class EventEnvelopeParser {
    private final ObjectMapper objectMapper;

    public <T> EventEnvelope<T> parse(String json, String topic, Class<T> payloadType) {
        try {
            JavaType type = objectMapper.getTypeFactory()
                    .constructParametricType(EventEnvelope.class, payloadType);
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException(topic + " parse 실패", e);
        }
    }
}
