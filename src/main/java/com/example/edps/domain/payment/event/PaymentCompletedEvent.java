package com.example.edps.domain.payment.event;

import com.example.edps.domain.payment.entity.PaymentLog;
import com.example.edps.domain.payment.enums.PayStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PaymentCompletedEvent(
        Long orderId,
        Long paymentId,
        String pgTxId,

        LocalDateTime requestedAt,

        // 응답 시각
        LocalDateTime respondedAt,

        PayStatus status,
        String failureReason,
        int attemptNo
) {
    public PaymentLog toPaymentLog() {
        return PaymentLog.builder()
                .pgTxId(pgTxId)
                .attemptNo(attemptNo)
                .requestedAt(requestedAt)
                .respondedAt(respondedAt)
                .status(status)
                .failureReason(failureReason)
                .build();
    }
}
