package com.example.edps.infra.kafka.consumer.dlq;

import com.example.edps.domain.payment.event.PaymentCompletedEvent;
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
public class PaymentResultDlqConsumer {
    private final EventEnvelopeParser eventEnvelopeParser;
    private final PaymentDlqLogRepository paymentDlqLogRepository;
    private final SlackNotifier slackNotifier;

    @KafkaListener(topics = KafkaTopics.PAYMENT_RESULT_DLQ, groupId = "payment-result-dlq")
    public void dlq(
            String value,
            @Header(value = KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, required = false) String cause,
            @Header(value = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String message,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, required = false) String consumerGroup) {

        log.error("[DLQ] originalTopic={}, cause={}, message={}", originalTopic, cause, message);

        Long orderId = null;
        Long paymentId = null;
        String eventId = null;

        try {
            EventEnvelope<PaymentCompletedEvent> env =
                    eventEnvelopeParser.parse(value, KafkaTopics.PAYMENT_RESULT_DLQ, PaymentCompletedEvent.class);
            orderId = env.payload().orderId();
            paymentId = env.payload().paymentId();
            eventId = env.eventId();
        } catch (Exception e) {
            log.warn("[DLQ] payload 파싱 실패, orderId/paymentId null로 저장");
        }

        // 실패 건 DB 저장, Slack 알림
        paymentDlqLogRepository.save(
                PaymentDlqLog.builder()
                        .topic(KafkaTopics.PAYMENT_RESULT_DLQ)
                        .originalTopic(originalTopic)
                        .originalOffset(originalOffset)
                        .consumerGroup(consumerGroup)
                        .orderId(orderId)
                        .paymentId(paymentId)
                        .eventId(eventId)
                        .payload(value)
                        .cause(cause)
                        .errorMessage(message)
                        .build()
        );

        slackNotifier.sendDlqAlert(originalTopic, eventId, orderId, paymentId, cause, message);
    }
}
