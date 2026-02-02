package com.example.edps.domain.order.entity;

import com.example.edps.domain.order.enums.PayStatus;
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

    // 요청 시각
    private LocalDateTime requestedAt;

    // 응답 시각
    private LocalDateTime respondedAt;

    // 외부 결제 endpoint
    private String requestUri;

    private String requestBody;

    private String responseBody;

    // 이 시도의 성공/실패
    @Enumerated(EnumType.STRING)
    private PayStatus status;

    private String failureReason;

    @Builder
    public PaymentLog(String pgTxId, LocalDateTime requestedAt, LocalDateTime respondedAt, String requestUri,
                      String requestBody, String responseBody, PayStatus status, String failureReason) {
        this.pgTxId = pgTxId;
        this.requestedAt = requestedAt;
        this.respondedAt = respondedAt;
        this.requestUri = requestUri;
        this.requestBody = requestBody;
        this.responseBody = responseBody;
        this.status = status;
        this.failureReason = failureReason;
    }
}

