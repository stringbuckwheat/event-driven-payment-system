package com.example.edps.infra.outbox.service;

import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.outbox.entity.OutboxEvent;
import com.example.edps.infra.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OutboxService {
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public <T> OutboxEvent save(String topic, String key, EventEnvelope<T> envelope) {
        String json = objectMapper.writeValueAsString(envelope);

        OutboxEvent outbox = OutboxEvent.builder()
                .eventId(envelope.eventId())
                .traceId(envelope.traceId())
                .topic(topic)
                .type(envelope.type())
                .messageKey(key)
                .payloadJson(json)
                .createdAt(Instant.now())
                .build();

        return outboxRepository.save(outbox);
    }
}
