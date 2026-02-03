package com.example.edps.infra.pg;

public record PgPaymentRequest(
        Long paymentId,
        int amount
) {}
