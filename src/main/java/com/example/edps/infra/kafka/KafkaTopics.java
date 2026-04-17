package com.example.edps.infra.kafka;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopics {
    // 결제 요청
    public static final String PAYMENT_COMMAND_REQUESTED = "payment.command.requested";

    // 결제 성공/실패 이벤트 (후처리/정산/알림 등)
    public static final String PAYMENT_EVENT_SUCCEEDED = "payment.event.succeeded";
    public static final String PAYMENT_EVENT_FAILED = "payment.event.failed";

    // 결제 DLQ
    public static final String PAYMENT_COMMAND_REQUESTED_DLQ = "payment.command.requested.dlq";
    public static final String PAYMENT_RESULT_DLQ = "payment.result.dlq";
}
