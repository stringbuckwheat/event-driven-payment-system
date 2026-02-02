package com.example.edps.domain.payment.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 모든 kafka 메시지는 이 Envelope로 감싼다
 * (메타데이터 표준화)
 */
public record EventEnvelope<T>(
        String eventId, // 메시지 고유 ID
        String traceId, // 요청 추적 ID
        Instant occurredAt, // 이벤트 발생 시각
        String type, // 토픽과 동일하게 맞추기
        T payload
) {
    public static <T> EventEnvelope<T> of(String traceId, String type, T payload) {
        String eventId = UUID.randomUUID().toString();
        String safeTraceId = (traceId == null || traceId.isBlank()) ? eventId : traceId;
        return new EventEnvelope<>(eventId, safeTraceId, Instant.now(), type, payload);
    }
}