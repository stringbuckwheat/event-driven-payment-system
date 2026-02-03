package com.example.edps.infra.outbox.entity;

import com.example.edps.infra.outbox.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "outbox_event",
        indexes = {
                @Index(name = "idx_outbox_status_created", columnList = "status, createdAt"),
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
    @Column(nullable = false)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;

    @Column(nullable = false)
    private int publishAttempts;

    @Column(length = 500)
    private String lastError;

    public static OutboxEvent pending(
            String eventId,
            String traceId,
            String topic,
            String type,
            String messageKey,
            String payloadJson
    ) {
        OutboxEvent e = new OutboxEvent();
        e.eventId = eventId;
        e.traceId = traceId;
        e.topic = topic;
        e.type = type;
        e.messageKey = messageKey;
        e.payloadJson = payloadJson;
        e.status = OutboxStatus.PENDING;
        e.createdAt = Instant.now();
        e.publishAttempts = 0;
        return e;
    }

    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = Instant.now();
        this.lastError = null;
    }

    public void recordFailure(String error, int maxAttempts) {
        this.publishAttempts++;
        this.lastError = error;

        if (this.publishAttempts >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
        } else {
            // 재시도 대상이면 다시 PENDING으로 돌려놓는다
            this.status = OutboxStatus.PENDING;
        }
    }
}
