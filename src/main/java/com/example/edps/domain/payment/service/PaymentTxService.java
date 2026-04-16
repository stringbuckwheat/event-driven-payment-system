package com.example.edps.domain.payment.service;

import com.example.edps.domain.payment.entity.Payment;
import com.example.edps.domain.payment.entity.PaymentLog;
import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.event.PaymentCompletedEvent;
import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.domain.payment.repository.PaymentLogRepository;
import com.example.edps.domain.payment.repository.PaymentRepository;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.BusinessException;
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
    private final int maxAttempts = 5;

    /**
     * 결제 선점(CAS)
     * READY -> PROCESSING 성공하면 true, 다른 워커가 선점했거나 완료된 결제면 false
     *
     * @param paymentId 결제 ID
     * @return 선점 성공 여부
     */
    @Transactional
    public boolean claim(Long paymentId) {
        return paymentRepository.transitionStatus(paymentId, PayStatus.READY, PayStatus.PROCESSING) == 1;
    }

    /**
     * 결제 결과 확정
     * PG 응답 기반으로 결제 상태 확정, PaymentLog 기록, Outbox 적재, ProcessedEvent 저장
     *
     * @param cmd           결제 요청 커맨드
     * @param traceId       요청 추적 ID
     * @param eventId       Kafka 이벤트 ID (중복 처리 방지용)
     * @param status        결제 결과 상태
     * @param pgTxId        PG 트랜잭션 ID (성공 시에만 존재)
     * @param failureReason 실패 사유 (실패 시에만 존재)
     * @param requestedAt   PG 요청 시각
     * @param respondedAt   PG 응답 시각
     */
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
                .orElseThrow(() -> new BusinessException(ErrorType.PAYMENT_NOT_FOUND, "paymentId=" + cmd.paymentId()));

        // '결제중' 상태만 진행 가능
        if (payment.getStatus() != PayStatus.PROCESSING) {
            log.info("이미 확정된 결제 - skip paymentId={}, status={}", cmd.paymentId(), payment.getStatus());
            return;
        }

        // 결제 완료 처리
        payment.complete(status, pgTxId, requestedAt, respondedAt, failureReason);

        // 결제 시도 횟수
        int attemptNo = (int) paymentLogRepository.countByPaymentId(cmd.paymentId()) + 1;
        PaymentCompletedEvent event = buildCompletedEvent(cmd, pgTxId, status, failureReason, requestedAt, respondedAt, attemptNo);

        payment.addLog(event.toPaymentLog());

        // Outbox, ProcessedEvent 저장
        publishAndRecord(cmd.paymentId(), traceId, eventId, status, event);
    }

    /**
     * transient 오류 처리 (TX)
     * PG 일시적 오류 시 시도 횟수 기록 후 재시도 유도
     * 재시도 횟수 소진 시 결제 실패 확정 후 Outbox 적재
     *
     * @param cmd         결제 요청 커맨드
     * @param traceId     요청 추적 ID
     * @param eventId     Kafka 이벤트 ID
     * @param requestedAt PG 요청 시각
     * @param ex          발생한 예외
     */
    @Transactional
    public void handleTransientTx(PaymentRequestedCommand cmd,
                                  String traceId,
                                  String eventId,
                                  LocalDateTime requestedAt,
                                  Exception ex) {

        Payment payment = paymentRepository.findById(cmd.paymentId())
                .orElseThrow(() -> new BusinessException(ErrorType.PAYMENT_NOT_FOUND, "paymentId=" + cmd.paymentId()));

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
                        .build()
        );

        if (attemptNo >= maxAttempts) {
            exhaustTransient(cmd, traceId, eventId, payment, requestedAt, respondedAt, attemptNo);
            return;
        }

        // 다음 재시도를 위해 PROCESSING -> READY
        paymentRepository.transitionStatus(cmd.paymentId(), PayStatus.PROCESSING, PayStatus.READY);

        log.warn("transient -> retry paymentId={}, attemptNo={}, err={}", cmd.paymentId(), attemptNo, safeMsg(ex));
    }

    /**
     * PaymentCompletedEvent 생성
     *
     * @param cmd           결제 요청 커맨드
     * @param pgTxId        PG 트랜잭션 ID
     * @param status        결제 상태
     * @param failureReason 실패 사유
     * @param requestedAt   PG 요청 시각
     * @param respondedAt   PG 응답 시각
     * @param attemptNo     시도 횟수
     */
    private PaymentCompletedEvent buildCompletedEvent(PaymentRequestedCommand cmd,
                                                      String pgTxId,
                                                      PayStatus status,
                                                      String failureReason,
                                                      LocalDateTime requestedAt,
                                                      LocalDateTime respondedAt,
                                                      int attemptNo) {
        return PaymentCompletedEvent.builder()
                .orderId(cmd.orderId())
                .paymentId(cmd.paymentId())
                .pgTxId(pgTxId)
                .requestedAt(requestedAt)
                .respondedAt(respondedAt)
                .status(status)
                .failureReason(failureReason)
                .attemptNo(attemptNo)
                .build();
    }


    /**
     * Outbox 적재 + ProcessedEvent 저장
     * 단일 트랜잭션 안에서 호출되어 둘 다 원자적으로 커밋됨
     *
     * @param paymentId 결제 ID
     * @param traceId   요청 추적 ID
     * @param eventId   Kafka 이벤트 ID
     * @param status    결제 상태 (토픽 결정에 사용)
     * @param event     발행할 이벤트
     */
    private void publishAndRecord(Long paymentId, String traceId, String eventId, PayStatus status, PaymentCompletedEvent event) {
        String topic = resolvedTopic(status);
        outboxService.save(topic, String.valueOf(paymentId), EventEnvelope.of(traceId, topic, event));
    }

    private String resolvedTopic(PayStatus status) {
        return status == PayStatus.SUCCESS
                ? KafkaTopics.PAYMENT_EVENT_SUCCEEDED
                : KafkaTopics.PAYMENT_EVENT_FAILED;
    }

    /**
     * 재시도 소진 시 결제 실패 확정
     * 결제 FAILED 확정 → Outbox 적재 → ProcessedEvent 저장
     *
     * @param cmd         결제 요청 커맨드
     * @param traceId     요청 추적 ID
     * @param eventId     Kafka 이벤트 ID
     * @param payment     결제 엔티티
     * @param requestedAt PG 요청 시각
     * @param respondedAt 응답 시각
     * @param attemptNo   최종 시도 횟수
     */
    private void exhaustTransient(PaymentRequestedCommand cmd,
                                  String traceId,
                                  String eventId,
                                  Payment payment,
                                  LocalDateTime requestedAt,
                                  LocalDateTime respondedAt,
                                  int attemptNo) {
        String reason = "TRANSIENT_EXHAUSTED attempts=" + attemptNo;
        payment.complete(PayStatus.FAILED, null, requestedAt, respondedAt, reason);

        PaymentCompletedEvent failedEvent = buildCompletedEvent(
                cmd, null, PayStatus.FAILED, reason,
                payment.getRequestedAt() != null ? payment.getRequestedAt() : requestedAt,
                respondedAt, attemptNo);

        publishAndRecord(cmd.paymentId(), traceId, eventId, PayStatus.FAILED, failedEvent);
        log.warn("결제 실패(재시도 소진) paymentId={}, attempts={}", cmd.paymentId(), attemptNo);
    }

    private String safeMsg(Exception ex) {
        String m = ex.getMessage();
        if (m == null || m.isBlank()) return ex.getClass().getSimpleName();
        return m.length() <= 300 ? m : m.substring(0, 300);
    }
}
