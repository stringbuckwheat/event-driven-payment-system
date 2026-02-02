package com.example.edps.infra.kafka.consumer;

import com.example.edps.domain.payment.event.EventEnvelope;
import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.infra.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// 결제 요청 커맨드 소비
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCommandConsumer {
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMMAND_REQUESTED,
            groupId = "edps-payment-worker"
    )
    public void onPaymentRequested(EventEnvelope<?> envelope) {
        PaymentRequestedCommand cmd = objectMapper.convertValue(envelope.payload(), PaymentRequestedCommand.class);

        log.info("consume payment.requested eventId={}, paymentId={}, orderId={}, amount={}",
                envelope.eventId(), cmd.paymentId(), cmd.orderId(), cmd.amount());
    }
}
