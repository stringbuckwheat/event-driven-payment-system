package com.example.edps.infra.kafka;

import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.infra.kafka.producer.PaymentEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/debug/kafka")
@Validated
public class KafkaDebugController {
    private final PaymentEventProducer producer;

    @PostMapping("/payment-requested")
    public ResponseEntity<?> publishPaymentRequested(@RequestBody @Validated PaymentRequestedCommand cmd) {
        String traceId = UUID.randomUUID().toString();
        producer.publishPaymentRequested(traceId, cmd);

        return ResponseEntity.accepted().build();
    }
}
