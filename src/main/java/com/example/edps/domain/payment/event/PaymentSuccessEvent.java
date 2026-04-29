package com.example.edps.domain.payment.event;

public record PaymentSuccessEvent(String userId, Long orderId, int amount) {}
