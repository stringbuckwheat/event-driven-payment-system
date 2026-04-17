package com.example.edps.infra.kafka.consumer;

import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.dlq.PaymentDlqLog;
import com.example.edps.infra.kafka.dlq.PaymentDlqLogRepository;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.kafka.message.EventEnvelopeParser;
import com.example.edps.infra.notification.SlackNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCommandDlqConsumer {
    private final PaymentDlqLogRepository paymentDlqLogRepository;
    private final SlackNotifier slackNotifier;
    private final EventEnvelopeParser eventEnvelopeParser;

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMMAND_REQUESTED_DLQ, groupId = "payment-dlq")
    public void dlq(
            String value,
            @Header(value = KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, required = false) String cause, // 어떤 예외클래스인지
            @Header(value = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String message, // 예외 메시지
            @Header(value = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic, // 원본토픽
            @Header(value = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset, // 오프셋
            @Header(value = KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, required = false) String consumerGroup) {

        log.error("[DLQ] originalTopic={}, cause={}, message={}", originalTopic, cause, message);
        log.error("offset type={}, value={}", originalOffset.getClass(), originalOffset);

        Long orderId = null;
        Long paymentId = null;
        String eventId = null;

        try {
            EventEnvelope<PaymentRequestedCommand> env =
                    eventEnvelopeParser.parse(value, KafkaTopics.PAYMENT_COMMAND_REQUESTED_DLQ, PaymentRequestedCommand.class);
            orderId = env.payload().orderId();
            paymentId = env.payload().paymentId();
            eventId = env.eventId();
        } catch (Exception e) {
            log.warn("[DLQ] payload 파싱 실패, orderId/paymentId null로 저장");
        }

        // 실패건 DB 저장
        paymentDlqLogRepository.save(
                PaymentDlqLog.builder()
                        .topic(KafkaTopics.PAYMENT_COMMAND_REQUESTED_DLQ)
                        .originalTopic(originalTopic)
                        .originalOffset(originalOffset)
                        .consumerGroup(consumerGroup)
                        .orderId(orderId)
                        .paymentId(paymentId)
                        .eventId(eventId)
                        .payload(value)
                        .errorMessage(cause)
                        .build()
        );

        // 슬랙 알림
        slackNotifier.sendDlqAlert(originalTopic, eventId, orderId, paymentId, cause);
    }
}
