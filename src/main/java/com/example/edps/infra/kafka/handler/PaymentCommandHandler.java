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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentCommandHandler {
    private final PaymentTxService paymentTxService;
    private final PaymentClient paymentClient;
    private final RedissonClient redissonClient;

    // 오케스트레이션
    public void process(EventEnvelope<PaymentRequestedCommand> envelope) {
        PaymentRequestedCommand cmd = envelope.payload();

        // 1) Redis 분산락으로 워커 선점 (leaseTime=30s, 즉시 시도)
        RLock lock = redissonClient.getLock("payment-claim-" + cmd.paymentId());
        try {
            if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {
                log.info("이미 진행 중인 결제 - 락 획득 실패 paymentId={}", cmd.paymentId());
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        log.info("결제 선점 완료 paymentId={}", cmd.paymentId());

        try {
            LocalDateTime requestedAt = LocalDateTime.now();

            try {
                // 2) 트랜잭션 없이 PG 호출
                PgPaymentResponse res = paymentClient.requestPayment(cmd);
                LocalDateTime respondedAt = LocalDateTime.now();
                PayStatus status = res.isSuccess() ? PayStatus.SUCCESS : PayStatus.FAILED;
                log.info("PgPaymentResponse.status={}", status);

                // 3) TX: 결과 확정 + 로그 + Outbox 저장
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
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String safeReason(String reason) {
        if (!StringUtils.hasText(reason)) return null;
        return reason.length() <= 300 ? reason : reason.substring(0, 300);
    }
}
