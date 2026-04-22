package com.example.edps.infra.kafka.handler;

import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.domain.payment.service.PaymentTxService;
import com.example.edps.global.error.exception.PgBusinessException;
import com.example.edps.global.lock.DistributedLock;
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

    @DistributedLock(key = "'payment-claim-' + #envelope.payload().paymentId()")
    public void process(EventEnvelope<PaymentRequestedCommand> envelope) {
        PaymentRequestedCommand cmd = envelope.payload();
        log.info("결제 선점 완료 paymentId={}", cmd.paymentId());

        LocalDateTime requestedAt = LocalDateTime.now();

        try {
            // 트랜잭션 없이 PG 호출
            PgPaymentResponse res = paymentClient.requestPayment(cmd);
            LocalDateTime respondedAt = LocalDateTime.now();
            PayStatus status = res.isSuccess() ? PayStatus.SUCCESS : PayStatus.FAILED;
            log.info("PgPaymentResponse.status={}", status);

            // TX: 결과 확정 + 로그 + Outbox 저장
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
            // 비즈니스 실패: FAILED 확정
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
            // 일시적 오류: 시도 횟수 기록 후 Kafka 재전송 유도
            paymentTxService.handleTransientTx(cmd, envelope.traceId(), envelope.eventId(), requestedAt, ex);
            throw ex;
        }
    }

    private String safeReason(String reason) {
        if (!StringUtils.hasText(reason)) return null;
        return reason.length() <= 300 ? reason : reason.substring(0, 300);
    }
}