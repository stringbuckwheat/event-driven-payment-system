package com.example.edps.domain.payment.timeout;

import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.repository.OrderRepository;
import com.example.edps.domain.payment.entity.Payment;
import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.event.PaymentCompletedEvent;
import com.example.edps.domain.payment.repository.PaymentRepository;
import com.example.edps.domain.product.repository.ProductRepository;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentStuckRecoveryService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final OutboxService outboxService;

    static final long STUCK_MINUTES = 2;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverSingleOrder(Long orderId) {
        Order order = orderRepository.findOrderWithItemsById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));
        Payment payment = order.getPayment();

        // 1) 결제 상태 선점
        int updated = paymentRepository.transitionStatus(
                payment.getId(), PayStatus.PROCESSING, PayStatus.FAILED);

        if (updated == 0) {
            log.info("[RECOVER] 이미 처리된 결제 orderId={}", orderId);
            return;
        }

        // 2) 주문 실패
        order.markAsFailed();

        // 3) 재고 롤백
        order.getOrderItems().forEach(item ->
                productRepository.increaseStock(item.getProduct().getId(), item.getQuantity()));

        // 4) 실패 이벤트 발행
        PaymentCompletedEvent failedEvent = PaymentCompletedEvent.builder()
                .orderId(order.getId())
                .paymentId(payment.getId())
                .status(PayStatus.FAILED)
                .failureReason("TIMEOUT_STUCK minutes=" + STUCK_MINUTES)
                .requestedAt(payment.getRequestedAt())
                .respondedAt(LocalDateTime.now())
                .build();

        String traceId = UUID.randomUUID().toString();

        outboxService.save(
                KafkaTopics.PAYMENT_EVENT_FAILED,
                String.valueOf(payment.getId()),
                EventEnvelope.of(traceId, KafkaTopics.PAYMENT_EVENT_FAILED, failedEvent));

        log.warn("[RECOVER] stuck 결제 실패 확정 orderId={}, paymentId={}", order.getId(), payment.getId());
    }
}