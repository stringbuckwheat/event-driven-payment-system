package com.example.edps.domain.payment.entity;

import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.payment.enums.PayStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    private int total; // 결제 금액

    @Setter
    @Enumerated(EnumType.STRING)
    private PayStatus status;

    // PG사의 트랜잭션 ID (성공 시에만 세팅)
    private String pgTxId;

    // 최초 결제 요청 시각
    private LocalDateTime requestedAt;

    // 결제 완료(성공/실패) 확정 시각
    private LocalDateTime completedAt;

    // 실패 사유
    private String failureReason;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentLog> paymentLogs = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;

    public Payment(Order order) {
        this.total = order.getTotal();
        this.order = order;
        this.status = PayStatus.READY;
    }

    // 테스트용 생성자
    public Payment(int total, Order order, PayStatus status) {
        this.total = total;
        this.order = order;
        this.status = status;
    }

    public void addLog(PaymentLog log) {
        paymentLogs.add(log);
        log.setPayment(this);
    }

    public void complete(PayStatus status, String pgTxId, LocalDateTime requestedAt, LocalDateTime completedAt, String failureReason) {
        this.status = status;
        this.pgTxId = pgTxId;
        this.requestedAt = requestedAt;
        this.completedAt = completedAt;
        this.failureReason = failureReason;
    }
}

