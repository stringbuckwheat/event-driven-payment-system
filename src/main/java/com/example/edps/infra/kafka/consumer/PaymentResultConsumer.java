package com.example.edps.infra.kafka.consumer;

import com.example.edps.domain.cart.repository.CartRepository;
import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.enums.OrderStatus;
import com.example.edps.domain.order.repository.OrderRepository;
import com.example.edps.domain.payment.entity.Payment;
import com.example.edps.domain.payment.entity.PaymentLog;
import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.event.PaymentCompletedEvent;
import com.example.edps.domain.payment.repository.PaymentRepository;
import com.example.edps.domain.product.repository.ProductRepository;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.ElementNotFoundException;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.message.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {
    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    private void completeOrder(Long orderId, Long paymentId, PaymentCompletedEvent result) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ElementNotFoundException(ErrorType.ORDER_NOT_FOUND, "orderId=" + orderId));

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ElementNotFoundException(ErrorType.PAYMENT_NOT_FOUND, "paymentId=" + paymentId));

        // 0. 진입 가드
        if (payment.getStatus() != PayStatus.PROCESSING) {
            return;
        }

        // 1. Payment 업데이트, PaymentLog Insert
        payment.complete(
                result.status(),
                result.pgTxId(),
                result.requestedAt(),
                result.respondedAt(),
                result.failureReason()
        );

        PaymentLog paymentLog = result.toPaymentLog();
        payment.addLog(paymentLog);

        // 2. Order 상태 변경
        OrderStatus orderStatus = PayStatus.SUCCESS.equals(result.status()) ? OrderStatus.PAID : OrderStatus.FAILED;
        order.setStatus(orderStatus);

        // 3. 성공이면 카트 삭제, 실패면 재고 롤백
        if (orderStatus.equals(OrderStatus.PAID)) {
            // 성공이면 장바구니 비우기
            cartRepository.deleteById(order.getUserId());
        } else {
            // 재고 롤백
            order.getOrderItems().forEach(item -> productRepository.increaseStock(item.getProduct().getId(), item.getQuantity()));
        }
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENT_SUCCEEDED, groupId = "payment-result")
    public void onSuccess(String value) {
        EventEnvelope<PaymentCompletedEvent> envelope = parse(value);
        PaymentCompletedEvent event = envelope.payload();

        completeOrder(event.orderId(), event.paymentId(), event);
        log.info("payment result applied: COMPLETED orderId={}, paymentId={}, eventId={}",
                event.orderId(), event.paymentId(), envelope.eventId());
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENT_FAILED, groupId = "payment-result")
    public void onFailed(String value) {
        EventEnvelope<PaymentCompletedEvent> envelope = parse(value);
        PaymentCompletedEvent event = envelope.payload();

        completeOrder(event.orderId(), event.paymentId(), event);

        log.info("payment result applied: FAILED orderId={}, paymentId={}, reason={}, eventId={}",
                event.orderId(), event.paymentId(), event.failureReason(), envelope.eventId());
    }

    private <T> EventEnvelope<T> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("payment result parse failed", e);
        }
    }

}
