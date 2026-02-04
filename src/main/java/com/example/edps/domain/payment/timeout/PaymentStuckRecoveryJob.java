package com.example.edps.domain.payment.timeout;

import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.enums.OrderStatus;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStuckRecoveryJob {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final OutboxService outboxService;

    private static final long STUCK_MINUTES = 2;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void recover() {
        log.info("[RECOVER] payment stuck recovery");
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STUCK_MINUTES);

        // TODO limit, 페이징 처리
        List<Order> stuckOrders = orderRepository.findStuckOrdersWithItems(
                PayStatus.PROCESSING,
                cutoff,
                OrderStatus.PAID,
                OrderStatus.FAILED
        );

        if (stuckOrders.isEmpty()) {
            log.info("[RECOVER] stuck 주문 없음");
            return;
        }

        for (Order order : stuckOrders) {
            Payment payment = order.getPayment();

            // 1) 결제 상태 선점
            int updated = paymentRepository.transitionStatus(
                    payment.getId(),
                    PayStatus.PROCESSING,
                    PayStatus.FAILED
            );

            if (updated == 0) continue;

            // 2) 주문 실패
            order.setStatus(OrderStatus.FAILED);

            // 3) 재고 롤백
            order.getOrderItems().forEach(item ->
                    productRepository.increaseStock(item.getProduct().getId(), item.getQuantity())
            );

            // 4) 실패 이벤트 발행
            PaymentCompletedEvent failedEvent = PaymentCompletedEvent.builder()
                    .orderId(order.getId())
                    .paymentId(payment.getId())
                    .status(PayStatus.FAILED)
                    .failureReason("TIMEOUT_STUCK minutes=" + STUCK_MINUTES)
                    .requestedAt(payment.getRequestedAt())
                    .respondedAt(LocalDateTime.now())
                    .build();

            outboxService.save(
                    KafkaTopics.PAYMENT_EVENT_FAILED,
                    String.valueOf(payment.getId()),
                    EventEnvelope.of("stuck-recovery", KafkaTopics.PAYMENT_EVENT_FAILED, failedEvent)
            );

            log.warn("stuck 결제 실패 확정 orderId={}, paymentId={}", order.getId(), payment.getId());
        }
    }
}


