package com.example.edps.infra.kafka.consumer;

import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.pg.PaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCommandConsumer {
    private final ObjectMapper objectMapper;
    private final PaymentClient paymentClient;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMMAND_REQUESTED,
            groupId = "edps-1"
    )
    public void onMessage(String key, String value) {
        try {
            log.info("수신확인! topic={}, key={}, value={}",
                    KafkaTopics.PAYMENT_COMMAND_REQUESTED, key, value);

            EventEnvelope<PaymentRequestedCommand> envelope =
                    objectMapper.readValue(
                            value,
                            new TypeReference<EventEnvelope<PaymentRequestedCommand>>() {}
                    );

            PaymentRequestedCommand cmd = envelope.payload();

            log.info("결제 요청 수신 eventId={}, paymentId={}, amount={}", envelope.eventId(), cmd.paymentId(), cmd.total());

            // pg 호출
            paymentClient.requestPayment(cmd.paymentId(), cmd.total(), cmd.scenario());
        } catch (Exception e) {
            log.error("결제 요청 처리 실패 message={}", value, e);
            throw new RuntimeException(e); // 재시도 유도
        }
    }
}