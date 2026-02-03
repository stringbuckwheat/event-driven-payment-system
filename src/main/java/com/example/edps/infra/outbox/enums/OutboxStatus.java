package com.example.edps.infra.outbox.enums;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}