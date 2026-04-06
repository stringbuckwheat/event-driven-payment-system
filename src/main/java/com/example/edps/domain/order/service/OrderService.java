package com.example.edps.domain.order.service;

import com.example.edps.domain.cart.model.Cart;
import com.example.edps.domain.cart.repository.CartRepository;
import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.entity.OrderItem;
import com.example.edps.domain.payment.entity.Payment;
import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.order.repository.OrderRepository;
import com.example.edps.domain.payment.repository.PaymentRepository;
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
        // tx1) 재고 검증, 각종 validation, Order 생성
        PaymentRequestedCommand command = createOrder(userId);

        // 결제 요청 커맨드를 Outbox에 적재
        publishPaymentRequestedOutbox(command);
    }

    private PaymentRequestedCommand createOrder(String userId) {
        log.info("========================= OrderService ========================");
        Cart cart = cartRepository.findById(userId)
                .orElseThrow(() -> new ElementNotFoundException(ErrorType.EMPTY_CART, "no cart, userId=" + userId));

        Map<Long, Integer> items = cart.getItems();
        if (items == null || items.isEmpty()) {
            throw new ElementNotFoundException(ErrorType.EMPTY_CART, "empty cart, userId=" + userId);
        }

        List<Long> productIds = new ArrayList<>(items.keySet());
        List<Product> products = productRepository.findAllByIdIn(productIds);
        List<OrderItem> orderItems = new ArrayList<>();
        int total = 0;

        for (Product p : products) {
            int qty = items.get(p.getId());

            // 재고차감
            int updated = productRepository.reduceStock(p.getId(), qty);
            if (updated == 0) {
                throw new SoldOutException(ErrorType.NOT_ENOUGH_STOCK, p.getId(), qty);
            }

            total += qty * p.getPrice();
            orderItems.add(new OrderItem(p, qty, p.getPrice()));
        }

        Order order = Order.create(userId, orderItems, total);
        order.attachPayment(new Payment(order));
        orderRepository.save(order);

        String scenario = "";
        return PaymentRequestedCommand.from(order, scenario);
    }

    private void publishPaymentRequestedOutbox(PaymentRequestedCommand cmd) {
        EventEnvelope<PaymentRequestedCommand> envelope
                = EventEnvelope.of(cmd.userId(), KafkaTopics.PAYMENT_COMMAND_REQUESTED, cmd);
        String key = String.valueOf(cmd.paymentId());
        outboxService.save(KafkaTopics.PAYMENT_COMMAND_REQUESTED, key, envelope);
        log.info("outbox 적재 완료 eventId={}, paymentId={}", envelope.eventId(), cmd.paymentId());
    }
}

