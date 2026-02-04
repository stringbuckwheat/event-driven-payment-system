package com.example.edps.domain.payment.enums;

public enum PayStatus {
    READY, // 결제 엔티티 생성
    REQUESTED, // 결제 커맨드 Outbox 저장
    PROCESSING, // 워커가 pg 호출(선점)
    SUCCESS,
    FAILED;
}

