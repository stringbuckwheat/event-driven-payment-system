package com.example.edps.domain.payment.service;

import com.example.edps.domain.payment.entity.Payment;
import com.example.edps.domain.payment.entity.PaymentLog;
import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.event.PaymentCompletedEvent;
import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.domain.payment.repository.PaymentLogRepository;
import com.example.edps.domain.payment.repository.PaymentRepository;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.ElementNotFoundException;
import com.example.edps.infra.idempotency.ProcessedEvent;
import com.example.edps.infra.idempotency.ProcessedEventRepository;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentTxService {
    private final PaymentRepository paymentRepository;
    private final PaymentLogRepository paymentLogRepository;
    private final OutboxService outboxService;
    private final ProcessedEventRepository processedEventRepository;
    private final int maxAttempts = 5;

    @Transactional
    public boolean claim(Long paymentId) {
        return paymentRepository.transitionStatus(paymentId, PayStatus.REQUESTED, PayStatus.PROCESSING) == 1;
    }

    @Transactional
    public void confirm(PaymentRequestedCommand cmd,
                        String traceId,
                        String eventId,
                        PayStatus status,
                        String pgTxId,
                        String failureReason,
                        LocalDateTime requestedAt,
                        LocalDateTime respondedAt) {

        Payment payment = paymentRepository.findById(cmd.paymentId())
                .orElseThrow(() -> new ElementNotFoundException(ErrorType.PAYMENT_NOT_FOUND, "paymentId=" + cmd.paymentId()));

        if (payment.getStatus() != PayStatus.PROCESSING) {
            log.info("이미 확정된 결제 - skip paymentId={}, status={}", cmd.paymentId(), payment.getStatus());
            return;
        }

        payment.complete(status, pgTxId, requestedAt, respondedAt, failureReason);

        int attemptNo = (int) paymentLogRepository.countByPaymentId(cmd.paymentId()) + 1;

        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .orderId(cmd.orderId())
                .paymentId(cmd.paymentId())
                .pgTxId(pgTxId)
                .requestedAt(requestedAt)
                .respondedAt(respondedAt)
                .status(status)
                .failureReason(failureReason)
                .attemptNo(attemptNo)
                .build();

        payment.addLog(event.toPaymentLog());

        String topic = (status == PayStatus.SUCCESS)
                ? KafkaTopics.PAYMENT_EVENT_SUCCEEDED
                : KafkaTopics.PAYMENT_EVENT_FAILED;

        outboxService.save(topic, String.valueOf(cmd.paymentId()), EventEnvelope.of(traceId, topic, event));

        // 처리 완료 기록 (동일 eventId 재전달 방지)
        processedEventRepository.save(new ProcessedEvent(eventId));

        log.info("결제 결과 확정 paymentId={}, status={}, outboxTopic={} / {}원", cmd.paymentId(), status, topic, cmd.total());
    }

    @Transactional
    public void handleTransientTx(PaymentRequestedCommand cmd,
                                  String traceId,
                                  String eventId,
                                  LocalDateTime requestedAt,
                                  Exception ex) {

        Payment payment = paymentRepository.findById(cmd.paymentId())
                .orElseThrow(() -> new ElementNotFoundException(ErrorType.PAYMENT_NOT_FOUND, "paymentId=" + cmd.paymentId()));

        if (payment.getStatus() != PayStatus.PROCESSING) {
            log.info("이미 확정된 결제 - skip transient paymentId={}, status={}", cmd.paymentId(), payment.getStatus());
            return;
        }

        int attemptNo = (int) paymentLogRepository.countByPaymentId(cmd.paymentId()) + 1;
        LocalDateTime respondedAt = LocalDateTime.now();

        payment.addLog(
                PaymentLog.builder()
                        .attemptNo(attemptNo)
                        .requestedAt(requestedAt)
                        .respondedAt(respondedAt)
                        .status(PayStatus.PROCESSING)
                        .failureReason("TRANSIENT: " + safeMsg(ex))
                        .httpStatus(null)
                        .build()
        );

        if (attemptNo >= maxAttempts) {
            String reason = "TRANSIENT_EXHAUSTED attempts=" + attemptNo;
            payment.complete(PayStatus.FAILED, null, requestedAt, respondedAt, reason);

            PaymentCompletedEvent failedEvent = PaymentCompletedEvent.builder()
                    .orderId(cmd.orderId())
                    .paymentId(cmd.paymentId())
                    .pgTxId(null)
                    .requestedAt(payment.getRequestedAt() != null ? payment.getRequestedAt() : requestedAt)
                    .respondedAt(respondedAt)
                    .status(PayStatus.FAILED)
                    .failureReason(reason)
                    .attemptNo(attemptNo)
                    .build();

            outboxService.save(KafkaTopics.PAYMENT_EVENT_FAILED, String.valueOf(cmd.paymentId()),
                    EventEnvelope.of(traceId, KafkaTopics.PAYMENT_EVENT_FAILED, failedEvent));

            // 처리 완료 기록 (동일 eventId 재전달 방지)
            processedEventRepository.save(new ProcessedEvent(eventId));

            log.warn("결제 실패(재시도 소진) paymentId={}, attempts={}", cmd.paymentId(), attemptNo);
            return;
        }

        // 다음 재시도를 위해 PROCESSING -> READY
        paymentRepository.transitionStatus(cmd.paymentId(), PayStatus.PROCESSING, PayStatus.READY);

        log.warn("transient -> retry paymentId={}, attemptNo={}, err={}", cmd.paymentId(), attemptNo, safeMsg(ex));
    }

    private String safeMsg(Exception ex) {
        String m = ex.getMessage();
        if (m == null || m.isBlank()) return ex.getClass().getSimpleName();
        return m.length() <= 300 ? m : m.substring(0, 300);
    }
}
