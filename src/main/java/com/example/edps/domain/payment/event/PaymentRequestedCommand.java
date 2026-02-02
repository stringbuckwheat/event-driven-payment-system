package com.example.edps.domain.payment.event;

// 결제 워커가 소비하여 PG를 호출하는 메시지
public record PaymentRequestedCommand(
        Long orderId,
        Long paymentId,
        String orderNumber,
        String userId,
        int amount
) {}