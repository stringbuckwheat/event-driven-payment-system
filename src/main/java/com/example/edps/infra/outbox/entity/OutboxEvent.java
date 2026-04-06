package com.example.edps.infra.outbox.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "outbox_event",
        indexes = {
                @Index(name = "idx_outbox_event_id", columnList = "eventId", unique = true)
        }
)
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80, unique = true)
    private String eventId;

    @Column(nullable = false, length = 80)
    private String traceId;

    @Column(nullable = false, length = 120)
    private String topic;

    @Column(nullable = false, length = 120)
    private String type;

    @Column(nullable = false, length = 80)
    private String messageKey;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(nullable = false)
    private Instant createdAt;
}