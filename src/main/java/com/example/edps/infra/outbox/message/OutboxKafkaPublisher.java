package com.example.edps.infra.outbox.message;

import com.example.edps.infra.outbox.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class OutboxKafkaPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public void publish(OutboxEvent e) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(e.getTopic(), e.getMessageKey(), e.getPayloadJson());

        record.headers().add("eventId", e.getEventId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("traceId", e.getTraceId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("type", e.getType().getBytes(StandardCharsets.UTF_8));

        try {
            kafkaTemplate.send(record).get(); // 블로킹
        } catch (Exception ex) {
            throw new RuntimeException("kafka publish 실패", ex);
        }
    }
}
