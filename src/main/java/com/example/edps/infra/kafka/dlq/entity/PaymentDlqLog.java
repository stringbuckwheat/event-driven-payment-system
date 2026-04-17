package com.example.edps.infra.kafka.dlq.entity;

import com.example.edps.infra.kafka.dlq.enums.ReprocessStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentDlqLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventId;
    private Long orderId;
    private Long paymentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    private String cause;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private String topic;

    private String originalTopic;
    private Long originalOffset;
    private String consumerGroup;

    @Enumerated(EnumType.STRING) // 재처리 여부
    private ReprocessStatus reprocessStatus;

    private LocalDateTime reprocessedAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @Builder
    public PaymentDlqLog(String eventId, Long orderId, Long paymentId,
                         String payload, String cause, String errorMessage, String topic,
                         String originalTopic, Long originalOffset, String consumerGroup) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.payload = payload;
        this.cause = cause;
        this.errorMessage = errorMessage;
        this.topic = topic;
        this.originalTopic = originalTopic;
        this.originalOffset = originalOffset;
        this.consumerGroup = consumerGroup;
    }

    public void markReprocessed() {
        this.reprocessStatus = ReprocessStatus.REPROCESSED;
        this.reprocessedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.reprocessStatus = ReprocessStatus.FAILED;
        this.reprocessedAt = LocalDateTime.now();
    }
}