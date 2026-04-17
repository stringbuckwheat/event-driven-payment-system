package com.example.edps.infra.kafka.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

@Slf4j
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
            String preview = json != null && json.length() > 200 ? json.substring(0, 200) + "..." : json;
            log.error("[EventEnvelopeParser] parse 실패 topic={}, payload={}", topic, preview, e);
            throw new RuntimeException(topic + " parse 실패", e);
        }
    }
}
