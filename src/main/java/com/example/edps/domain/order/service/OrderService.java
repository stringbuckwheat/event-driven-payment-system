package com.example.edps.domain.order.service;

import com.example.edps.domain.cart.model.Cart;
import com.example.edps.domain.cart.repository.CartRepository;
import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.entity.OrderItem;
import com.example.edps.domain.order.entity.Payment;
import com.example.edps.domain.order.enums.PayStatus;
import com.example.edps.domain.order.repository.OrderRepository;
import com.example.edps.domain.order.repository.PaymentRepository;
import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.domain.product.entity.Product;
import com.example.edps.domain.product.repository.ProductRepository;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.ElementNotFoundException;
import com.example.edps.global.error.exception.PaymentInProgressException;
import com.example.edps.global.error.exception.SoldOutException;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;

    @Transactional
    public void order(String userId) {
        // tx1)
        // 1. 재고 검증, 각종 validation, Order 생성
        PaymentRequestedCommand command = createOrder(userId);

        // 2. 결제 선점
        // TODO stuck 처리
        acquirePaymentProcessing(command.paymentId());
        log.info("결제 선점 완료");

        // 3. 결제 요청 커맨드를 Outbox에 적재
        publishPaymentRequestedOutbox(command);
    }

    private PaymentRequestedCommand createOrder(String userId) {
        Cart cart = cartRepository.findById(userId)
                .orElseThrow(() -> new ElementNotFoundException(ErrorType.EMPTY_CART, "no cart, userId=" + userId));

        Map<Long, Integer> items = cart.getItems();
        if (items == null || items.isEmpty()) {
            throw new ElementNotFoundException(ErrorType.EMPTY_CART, "empty cart, userId=" + userId);
        }

        List<Long> productIds = new ArrayList<>(items.keySet());
        List<Product> products = productRepository.findAllByIdIn(productIds);

        if (products.size() != productIds.size()) {
            // 장바구니에 있는데 상품이 없는 케이스(삭제/비공개 등)
            throw new ElementNotFoundException(ErrorType.PRODUCT_NOT_FOUND, "some products missing, userId=" + userId);
        }

        // 재고 차감
        for (Product p : products) {
            int qty = items.get(p.getId());
            int updated = productRepository.reduceStock(p.getId(), qty);
            if (updated == 0) {
                throw new SoldOutException(ErrorType.NOT_ENOUGH_STOCK, p.getId(), qty);
            }
        }

        List<OrderItem> orderItems = new ArrayList<>();

        for (Product p : products) {
            int qty = items.get(p.getId());
            orderItems.add(new OrderItem(p, qty, p.getPrice()));
        }

        Order order = Order.create(userId, orderItems);
        orderRepository.save(order);

        Payment payment = new Payment(order);
        order.attachPayment(payment);
        paymentRepository.save(payment);

        return PaymentRequestedCommand.from(order);
    }

    private void acquirePaymentProcessing(Long paymentId) {
        int updated = paymentRepository.transitionStatus(
                paymentId,
                PayStatus.READY,
                PayStatus.PROCESSING
        );

        // 선점 실패면 이미 진행중인 결제 -> PG 호출 금지
        if (updated == 0) {
            throw new PaymentInProgressException();
        }
    }

    private void publishPaymentRequestedOutbox(PaymentRequestedCommand cmd) {
        EventEnvelope<PaymentRequestedCommand> envelope
                = EventEnvelope.of(cmd.userId(), KafkaTopics.PAYMENT_COMMAND_REQUESTED, cmd);
        String key = String.valueOf(cmd.paymentId());
        outboxService.save(KafkaTopics.PAYMENT_COMMAND_REQUESTED, key, envelope);
        log.info("outbox 적재 완료 eventId={}, paymentId={}", envelope.eventId(), cmd.paymentId());
    }
}

