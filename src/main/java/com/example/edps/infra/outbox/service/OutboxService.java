package com.example.edps.infra.outbox.service;

import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.JsonException;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.outbox.entity.OutboxEvent;
import com.example.edps.infra.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxService {
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public <T> OutboxEvent save(String topic, String key, EventEnvelope<T> envelope) {
        try {
            String json = objectMapper.writeValueAsString(envelope);

            OutboxEvent outbox = OutboxEvent.pending(
                    envelope.eventId(),
                    envelope.traceId(),
                    topic,
                    envelope.type(),
                    key,
                    json
            );

            return outboxRepository.save(outbox);
        } catch (JacksonException e) {
            throw new JsonException(ErrorType.SERIALIZE_FAIL, "outbox payload serialize 실패", e);
        }
    }
}
