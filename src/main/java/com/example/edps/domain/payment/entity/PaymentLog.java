package com.example.edps.domain.payment.entity;

import com.example.edps.domain.payment.enums.PayStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String pgTxId;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    private Payment payment;

    private Integer attemptNo; // 재시도 몇 번째인지

    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;

    @Enumerated(EnumType.STRING)
    private PayStatus status;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Builder
    public PaymentLog(String pgTxId, Integer attemptNo,
                      LocalDateTime requestedAt, LocalDateTime respondedAt,
                      PayStatus status, String failureReason) {
        this.pgTxId = pgTxId;
        this.attemptNo = attemptNo;
        this.requestedAt = requestedAt;
        this.respondedAt = respondedAt;
        this.status = status;
        this.failureReason = failureReason;
    }
}
