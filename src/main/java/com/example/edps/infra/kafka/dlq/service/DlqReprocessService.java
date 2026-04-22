package com.example.edps.infra.kafka.dlq.service;

import com.example.edps.domain.payment.entity.Payment;
import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.event.PaymentCompletedEvent;
import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.domain.payment.repository.PaymentRepository;
import com.example.edps.domain.payment.service.PaymentResultTxService;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.BusinessException;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.dlq.entity.PaymentDlqLog;
import com.example.edps.infra.kafka.dlq.enums.ReprocessStatus;
import com.example.edps.infra.kafka.dlq.repository.PaymentDlqLogRepository;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.kafka.message.EventEnvelopeParser;
import com.example.edps.infra.outbox.service.OutboxService;
import com.example.edps.global.lock.DistributedLock;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DlqReprocessService {
    private final PaymentDlqLogRepository paymentDlqLogRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentResultTxService paymentResultTxService;
    private final OutboxService outboxService;
    private final EventEnvelopeParser eventEnvelopeParser;

    /**
     * DLQ 실패 건 재처리
     * 분산락으로 중복 재처리 방지 (락 획득 실패 시 LockAcquisitionException)
     * originalTopic으로 분기하여 적절한 재처리 수행
     *
     * @param dlqLogId PaymentDlqLog PK
     */
    @DistributedLock(key = "'dlq-reprocess-' + #dlqLogId", throwOnFail = true)
    public void reprocess(Long dlqLogId) {
        doReprocess(dlqLogId);
    }

    @Transactional
    public void doReprocess(Long dlqLogId) {
        PaymentDlqLog dlqLog = paymentDlqLogRepository.findById(dlqLogId)
                .orElseThrow(() -> new BusinessException(ErrorType.DLQ_LOG_NOT_FOUND, "dlqLogId=" + dlqLogId));

        // 이미 재처리 완료된 건은 재처리 불가
        if (dlqLog.getReprocessStatus() == ReprocessStatus.REPROCESSED) {
            throw new BusinessException(ErrorType.LOCK_ACQUISITION_FAILED, "dlqLogId=" + dlqLogId);
        }

        // 적절한 재처리 메소드 호출
        try {
            switch (dlqLog.getOriginalTopic()) {
                // 결제 요청 실패
                case KafkaTopics.PAYMENT_COMMAND_REQUESTED -> reprocessCommand(dlqLog);

                // 결제 후처리 실패
                case KafkaTopics.PAYMENT_EVENT_SUCCEEDED,
                     KafkaTopics.PAYMENT_EVENT_FAILED -> reprocessResult(dlqLog);

                default -> throw new BusinessException(ErrorType.UNSUPPORTED_DLQ_TOPIC,
                        "topic=" + dlqLog.getOriginalTopic());
            }

            // 재처리 상태 업데이트
            dlqLog.markReprocessed();
            log.info("[DLQ 재처리 완료] dlqLogId={}, originalTopic={}", dlqLogId, dlqLog.getOriginalTopic());
        } catch (Exception e) {
            // 재처리 실패 시 FAILED 기록 후 예외 재전파
            dlqLog.markFailed();
            log.error("[DLQ 재처리 실패] dlqLogId={}, cause={}", dlqLogId, e.getMessage());
            throw e;
        }
    }

    /**
     * PAYMENT_COMMAND_REQUESTED 재처리
     * 이미 확정된(SUCCESS/FAILED) 결제는 재처리 불가
     *
     * @param dlqLog DLQ 로그
     */
    private void reprocessCommand(PaymentDlqLog dlqLog) {
        Payment payment = paymentRepository.findById(dlqLog.getPaymentId())
                .orElseThrow(() -> new BusinessException(ErrorType.PAYMENT_NOT_FOUND, "paymentId=" + dlqLog.getPaymentId()));

        // 이미 확정된 결제는 재처리 불가
        if (payment.getStatus() == PayStatus.SUCCESS || payment.getStatus() == PayStatus.FAILED) {
            throw new BusinessException(ErrorType.PAYMENT_NOT_REPROCESSABLE,
                    "paymentId=" + dlqLog.getPaymentId() + ", status=" + payment.getStatus());
        }

        EventEnvelope<PaymentRequestedCommand> env =
                eventEnvelopeParser.parse(dlqLog.getPayload(), dlqLog.getOriginalTopic(), PaymentRequestedCommand.class);

        String traceId = Span.current().getSpanContext().getTraceId();

        // Outbox 다시 저장
        outboxService.save(
                KafkaTopics.PAYMENT_COMMAND_REQUESTED,
                String.valueOf(dlqLog.getPaymentId()),
                EventEnvelope.of(traceId, KafkaTopics.PAYMENT_COMMAND_REQUESTED, env.payload())
        );

        log.info("[DLQ 재처리] COMMAND paymentId={}", dlqLog.getPaymentId());
    }

    /**
     * 결제 후처리 DLQ 재처리
     * ProcessedEvent 테이블의 멱등성으로 중복 처리 방어
     * (이미 처리된 eventId면 applySuccess/applyFailure 내부에서 skip되므로 재호출 가능)
     *
     * @param dlqLog DLQ 로그
     */
    private void reprocessResult(PaymentDlqLog dlqLog) {
        EventEnvelope<PaymentCompletedEvent> env =
                eventEnvelopeParser.parse(dlqLog.getPayload(), dlqLog.getOriginalTopic(), PaymentCompletedEvent.class);

        if (KafkaTopics.PAYMENT_EVENT_SUCCEEDED.equals(dlqLog.getOriginalTopic())) {
            // 결제 성공 재처리
            paymentResultTxService.applySuccess(env.payload(), env.eventId());
        } else {
            // 결제 실패 건 재처리
            paymentResultTxService.applyFailure(env.payload(), env.eventId());
        }

        log.info("[DLQ 재처리] RESULT originalTopic={}, paymentId={}", dlqLog.getOriginalTopic(), dlqLog.getPaymentId());
    }
}
