package com.example.edps.infra.pg.dto;

public record PgPaymentRequest(
        Long paymentId,
        int amount
) {}
