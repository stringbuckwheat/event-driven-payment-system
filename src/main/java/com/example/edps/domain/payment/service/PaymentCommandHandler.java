package com.example.edps.domain.payment.service;

import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.global.error.exception.PgBusinessException;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.pg.PaymentClient;
import com.example.edps.infra.pg.dto.PgPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentCommandHandler {
    private final PaymentTxService paymentTxService;
    private final PaymentClient paymentClient;

    // 오케스트레이션
    public void process(EventEnvelope<PaymentRequestedCommand> envelope) {
        String eventId = envelope.eventId();
        PaymentRequestedCommand cmd = envelope.payload();

        // 1) tx1: claim
        boolean claimed = paymentTxService.claim(cmd.paymentId());
        if (!claimed) {
            // TODO PaymentInProgressException?
            log.info("이미 진행 중이거나 완료된 결제 - skip paymentId={}", cmd.paymentId());
            return;
        }

        LocalDateTime requestedAt = LocalDateTime.now();

        try {
            // 2) 트랜잭션 없이 PG 호출
            PgPaymentResponse res = paymentClient.requestPayment(cmd.paymentId(), cmd.total(), cmd.scenario());
            LocalDateTime respondedAt = LocalDateTime.now();
            PayStatus status = "SUCCESS".equalsIgnoreCase(res.result()) ? PayStatus.SUCCESS : PayStatus.FAILED;

            // 3) Tx2: 결과 확정 + 로그 + outbox + ProcessedEvent 저장
            paymentTxService.confirm(cmd, envelope.traceId(), eventId, status, res.pgTxId(), safeReason(res.reason()), requestedAt, respondedAt);

        } catch (PgBusinessException be) {
            // 비즈니스 실패: Tx2로 FAILED 확정 + ProcessedEvent 저장
            paymentTxService.confirm(cmd, envelope.traceId(), eventId, PayStatus.FAILED, null, safeReason(be.getMessage()), requestedAt, LocalDateTime.now());

        } catch (Exception ex) {
            // transient: Tx로 attempt 기록 + 상태 조정 (재시도 소진 시 ProcessedEvent 저장)
            paymentTxService.handleTransientTx(cmd, envelope.traceId(), eventId, requestedAt, ex);
            throw new RuntimeException(ex); // Kafka 재시도
        }
    }

    private String safeReason(String reason) {
        if (!StringUtils.hasText(reason)) return null;
        return reason.length() <= 300 ? reason : reason.substring(0, 300);
    }
}
