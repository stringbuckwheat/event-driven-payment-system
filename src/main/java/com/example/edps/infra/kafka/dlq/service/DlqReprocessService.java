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
import com.example.edps.infra.kafka.dlq.repository.PaymentDlqLogRepository;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.kafka.message.EventEnvelopeParser;
import com.example.edps.infra.outbox.service.OutboxService;
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
     * originalTopic으로 분기하여 적절한 재처리 수행
     *
     * @param dlqLogId PaymentDlqLog PK
     */
    @Transactional
    public void reprocess(Long dlqLogId) {
        PaymentDlqLog dlqLog = paymentDlqLogRepository.findById(dlqLogId)
                .orElseThrow(() -> new BusinessException(ErrorType.DLQ_LOG_NOT_FOUND, "dlqLogId=" + dlqLogId));

        switch (dlqLog.getOriginalTopic()) {
            // 결제 요청 실패
            case KafkaTopics.PAYMENT_COMMAND_REQUESTED -> reprocessCommand(dlqLog);

            // 결제 성공/실패 후 후처리 실패
            case KafkaTopics.PAYMENT_EVENT_SUCCEEDED,
                 KafkaTopics.PAYMENT_EVENT_FAILED -> reprocessResult(dlqLog);

            default ->
                    throw new BusinessException(ErrorType.UNSUPPORTED_DLQ_TOPIC, "topic=" + dlqLog.getOriginalTopic());
        }

        log.info("[DLQ 재처리] dlqLogId={}, originalTopic={}", dlqLogId, dlqLog.getOriginalTopic());
    }

    /**
     * PAYMENT_COMMAND_REQUESTED 재처리
     * Payment 상태가 READY인 경우에만 Outbox 재적재
     * (PROCESSING == PaymentStuckRecoveryJob에 위임, FAILED == 별도 처리 필요 X)
     */
    private void reprocessCommand(PaymentDlqLog dlqLog) {
        // 결제 엔티티 조회
        Payment payment = paymentRepository.findById(dlqLog.getPaymentId())
                .orElseThrow(() -> new BusinessException(ErrorType.PAYMENT_NOT_FOUND,
                        "paymentId=" + dlqLog.getPaymentId()));

        // 결제 상태 검사 - Payment 상태가 READY인 경우에만 재처리 가능
        // (PROCESSING == PaymentStuckRecoveryJob에 위임, FAILED == 별도 처리 필요 X)
        if (payment.getStatus() != PayStatus.READY) {
            throw new BusinessException(ErrorType.PAYMENT_NOT_REPROCESSABLE,
                    "paymentId=" + dlqLog.getPaymentId() + ", status=" + payment.getStatus());
        }

        EventEnvelope<PaymentRequestedCommand> env =
                eventEnvelopeParser.parse(dlqLog.getPayload(), dlqLog.getOriginalTopic(), PaymentRequestedCommand.class);

        String traceId = Span.current().getSpanContext().getTraceId();

        // outbox 다시 저장
        outboxService.save(
                KafkaTopics.PAYMENT_COMMAND_REQUESTED,
                String.valueOf(dlqLog.getPaymentId()),
                EventEnvelope.of(traceId, KafkaTopics.PAYMENT_COMMAND_REQUESTED, env.payload())
        );

        log.info("[DLQ 재처리] COMMAND paymentId={}", dlqLog.getPaymentId());
    }

    /**
     * PAYMENT_EVENT_SUCCEEDED/FAILED 재처리
     * ProcessedEvent 멱등성으로 중복 처리 방지
     */
    private void reprocessResult(PaymentDlqLog dlqLog) {
        EventEnvelope<PaymentCompletedEvent> env =
                eventEnvelopeParser.parse(dlqLog.getPayload(), dlqLog.getOriginalTopic(), PaymentCompletedEvent.class);

        if (KafkaTopics.PAYMENT_EVENT_SUCCEEDED.equals(dlqLog.getOriginalTopic())) {
            // 결제 성공 후처리
            paymentResultTxService.applySuccess(env.payload(), env.eventId());
        } else {
            // 결제 실패 후처리
            paymentResultTxService.applyFailure(env.payload(), env.eventId());
        }

        log.info("[DLQ 재처리] RESULT originalTopic={}, paymentId={}", dlqLog.getOriginalTopic(), dlqLog.getPaymentId());
    }
}
