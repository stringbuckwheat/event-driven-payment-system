package com.example.edps.domain.payment.event;

import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.enums.PgScenario;

// 결제 워커가 소비하여 PG를 호출하는 메시지
public record PaymentRequestedCommand(
        Long orderId,
        Long paymentId,
        String userId,
        int total,
        PgScenario scenario
) {
    public static PaymentRequestedCommand from(Order order, PgScenario scenario) {
        return new PaymentRequestedCommand(
                order.getId(),
                order.getPayment().getId(),
                order.getUserId(),
                order.getTotal(),
                scenario
        );
    }
}