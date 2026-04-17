package com.example.edps.infra.kafka.handler;

import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.domain.payment.service.PaymentTxService;
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
        PaymentRequestedCommand cmd = envelope.payload();

        // 1) TX1: 결제 선점
        boolean claimed = paymentTxService.claim(cmd.paymentId());
        if (!claimed) {
            log.info("이미 진행 중이거나 완료된 결제 - skip paymentId={}", cmd.paymentId());
            return;
        }

        log.info("결제 선점 완료 paymentId={}", cmd.paymentId());

        LocalDateTime requestedAt = LocalDateTime.now();

        try {
            // 2) 트랜잭션 없이 PG 호출
            PgPaymentResponse res = paymentClient.requestPayment(cmd);
            LocalDateTime respondedAt = LocalDateTime.now();
            PayStatus status = res.isSuccess() ? PayStatus.SUCCESS : PayStatus.FAILED;
            log.info("PgPaymentResponse.status={}", status);

            // 3) Tx2: 결과 확정 + 로그 + outbox + ProcessedEvent 저장
            paymentTxService.confirm(
                    cmd,
                    envelope.traceId(),
                    envelope.eventId(),
                    status,
                    res.pgTxId(),
                    safeReason(res.reason()),
                    requestedAt,
                    respondedAt
            );

        } catch (PgBusinessException ex) {
            log.warn(ex.getMessage(), ex);
            // 비즈니스 실패: Tx2로 FAILED 확정 + ProcessedEvent 저장
            paymentTxService.confirm(
                    cmd,
                    envelope.traceId(),
                    envelope.eventId(),
                    PayStatus.FAILED,
                    null,
                    safeReason(ex.getMessage()),
                    requestedAt,
                    LocalDateTime.now()
            );

        } catch (RuntimeException ex) {
            log.warn(ex.getMessage(), ex);
            paymentTxService.handleTransientTx(cmd, envelope.traceId(), envelope.eventId(), requestedAt, ex);
            throw ex;
        }
    }

    private String safeReason(String reason) {
        if (!StringUtils.hasText(reason)) return null;
        return reason.length() <= 300 ? reason : reason.substring(0, 300);
    }
}
