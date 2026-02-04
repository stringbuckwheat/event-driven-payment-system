package com.example.edps.domain.order.dto;

import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.enums.OrderStatus;
import com.example.edps.domain.payment.entity.Payment;
import com.example.edps.domain.payment.enums.PayStatus;

import java.time.LocalDateTime;

public record OrderStatusResponse(
        Long orderId,
        String userId,
        OrderStatus orderStatus,
        int total,

        Long paymentId,
        PayStatus paymentStatus,
        String pgTxId,
        String failureReason,
        LocalDateTime requestedAt,
        LocalDateTime completedAt,

        LocalDateTime createdAt
) {
    public static OrderStatusResponse of(Order o, Payment p) {
        return new OrderStatusResponse(
                o.getId(),
                o.getUserId(),
                o.getStatus(),
                o.getTotal(),
                p.getId(),
                p.getStatus(),
                p.getPgTxId(),
                p.getFailureReason(),
                p.getRequestedAt(),
                p.getCompletedAt(),
                o.getCreatedAt()
        );
    }
}
