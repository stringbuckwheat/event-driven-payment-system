package com.example.edps.infra.kafka.consumer;

import com.example.edps.domain.payment.entity.Payment;
import com.example.edps.domain.payment.entity.PaymentLog;
import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.event.PaymentCompletedEvent;
import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.domain.payment.repository.PaymentLogRepository;
import com.example.edps.domain.payment.repository.PaymentRepository;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.ElementNotFoundException;
import com.example.edps.global.error.exception.PgBusinessException;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.outbox.service.OutboxService;
import com.example.edps.infra.pg.PaymentClient;
import com.example.edps.infra.pg.dto.PgPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCommandConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentClient paymentClient;
    private final PaymentRepository paymentRepository;
    private final PaymentLogRepository paymentLogRepository;
    private final OutboxService outboxService;

    private final int maxAttempts = 5;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMMAND_REQUESTED,
            groupId = "edps-payment-worker"
    )
    public void onMessage(@Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
                          String value) {
        EventEnvelope<PaymentRequestedCommand> env = parse(value);
        PaymentRequestedCommand cmd = env.payload();

        log.info("수신확인 topic={}, key={}, paymentId={}, orderId={}, eventId={}",
                KafkaTopics.PAYMENT_COMMAND_REQUESTED, key, cmd.paymentId(), cmd.orderId(), env.eventId());

        // 1) 중복 방지 선점 (READY -> PROCESSING)
        if (!claim(cmd.paymentId())) {
            log.info("이미 진행 중이거나 완료된 결제 - skip paymentId={}", cmd.paymentId());
            return;
        }

        LocalDateTime requestedAt = LocalDateTime.now();

        try {
            // 2) PG 호출
            PgPaymentResponse res = paymentClient.requestPayment(cmd.paymentId(), cmd.total(), cmd.scenario());
            LocalDateTime respondedAt = LocalDateTime.now();
            PayStatus payStatus = "SUCCESS".equalsIgnoreCase(res.result()) ? PayStatus.SUCCESS : PayStatus.FAILED;

            handleResult(
                    cmd,
                    env.traceId(),
                    payStatus,
                    res.pgTxId(),
                    safeReason(res.reason()),
                    requestedAt,
                    respondedAt
            );

        } catch (PgBusinessException be) {
            // 4xx: 비즈니스 실패 (재시도 X)
            handleResult(
                    cmd,
                    env.traceId(),
                    PayStatus.FAILED,
                    null,
                    safeReason(be.getMessage()),
                    requestedAt,
                    LocalDateTime.now()
            );
        } catch (Exception ex) {
            // timeout/5xx/네트워크: 재시도
            handleTransient(cmd, env.traceId(), requestedAt, ex);
            throw new RuntimeException(ex); // Kafka 재시도
        }
    }

    private EventEnvelope<PaymentRequestedCommand> parse(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("PAYMENT_REQUESTED parse 실패", e);
        }
    }

    /**
     * READY -> PROCESSING 선점. 실패 시 PG 호출 금지.
     */
    private boolean claim(Long paymentId) {
        return paymentRepository.transitionStatus(paymentId, PayStatus.READY, PayStatus.PROCESSING) == 1;
    }

    /**
     * SUCCESS / FAILED 확정 처리 + 로그 + 결과 이벤트(outbox)
     */
    private void handleResult(PaymentRequestedCommand cmd,
                              String traceId,
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

        // attemptNo: 기존 로그 count + 1
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

        // 결과 이벤트 발행(outbox)
        String topic = (status == PayStatus.SUCCESS)
                ? KafkaTopics.PAYMENT_EVENT_SUCCEEDED
                : KafkaTopics.PAYMENT_EVENT_FAILED;

        EventEnvelope<PaymentCompletedEvent> out = EventEnvelope.of(traceId, topic, event);
        outboxService.save(topic, String.valueOf(cmd.paymentId()), out);

        log.info("결제 결과 확정 paymentId={}, status={}, eventId={}, outboxTopic={}",
                cmd.paymentId(), status, out.eventId(), topic);
    }

    /**
     * transient(타임아웃/5xx/네트워크) 처리:
     * - attempt 기록
     * - attempts 소진 시 FAILED 확정 + 실패 이벤트 발행
     * - 아니면 PROCESSING -> READY로 돌려놓고 예외 던져서 Kafka 재시도
     */
    private void handleTransient(PaymentRequestedCommand cmd,
                                 String traceId,
                                 LocalDateTime requestedAt,
                                 Exception ex) {

        Payment payment = paymentRepository.findById(cmd.paymentId())
                .orElseThrow(() -> new ElementNotFoundException(ErrorType.PAYMENT_NOT_FOUND, "paymentId=" + cmd.paymentId()));

        // 이미 확정됐으면 무시
        if (payment.getStatus() != PayStatus.PROCESSING) {
            log.info("이미 확정된 결제 - skip transient paymentId={}, status={}", cmd.paymentId(), payment.getStatus());
            return;
        }

        int attemptNo = (int) paymentLogRepository.countByPaymentId(cmd.paymentId()) + 1;
        LocalDateTime respondedAt = LocalDateTime.now();

        // transient 시도 로그 남김 (status는 PROCESSING 유지)
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
                    .build();

            EventEnvelope<PaymentCompletedEvent> out = EventEnvelope.of(traceId, KafkaTopics.PAYMENT_EVENT_FAILED, failedEvent);
            outboxService.save(KafkaTopics.PAYMENT_EVENT_FAILED, String.valueOf(cmd.paymentId()), out);

            log.warn("결제 실패(재시도 소진) paymentId={}, attempts={}, eventId={}",
                    cmd.paymentId(), attemptNo, out.eventId());
            return;
        }

        // 다음 재시도를 위해 PROCESSING -> READY 되돌림
        paymentRepository.transitionStatus(cmd.paymentId(), PayStatus.PROCESSING, PayStatus.READY);

        log.warn("transient -> retry paymentId={}, attemptNo={}, err={}",
                cmd.paymentId(), attemptNo, safeMsg(ex));
    }

    private String safeMsg(Exception ex) {
        String m = ex.getMessage();
        if (m == null || m.isBlank()) return ex.getClass().getSimpleName();
        return m.length() <= 300 ? m : m.substring(0, 300);
    }

    private String safeReason(String reason) {
        if (!StringUtils.hasText(reason)) return null;
        return reason.length() <= 300 ? reason : reason.substring(0, 300);
    }
}
