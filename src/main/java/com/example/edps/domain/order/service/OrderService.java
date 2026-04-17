package com.example.edps.domain.order.service;

import com.example.edps.domain.cart.model.Cart;
import com.example.edps.domain.cart.repository.CartRepository;
import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.entity.OrderItem;
import com.example.edps.domain.order.enums.PgScenario;
import com.example.edps.domain.payment.entity.Payment;
import com.example.edps.domain.order.repository.OrderRepository;
import com.example.edps.domain.payment.event.PaymentRequestedCommand;
import com.example.edps.domain.product.entity.Product;
import com.example.edps.domain.product.repository.ProductRepository;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.BusinessException;
import com.example.edps.global.error.exception.SoldOutException;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.kafka.message.EventEnvelope;
import com.example.edps.infra.outbox.service.OutboxService;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OutboxService outboxService;

    /**
     * 주문 생성 및 결제 요청
     * TX1: 장바구니 검증, 재고 차감, Order/Payment 생성, Outbox 적재를 단일 트랜잭션으로 처리
     *
     * @param userId
     * @param scenario PG 호출 시나리오 (SUCCESS / TRANSIENT_FAIL / BUSINESS_FAIL)
     */
    @Transactional
    public void order(String userId, PgScenario scenario) {
        Order order = createOrder(userId);
        savePaymentCommandToOutbox(order, scenario);
    }

    /**
     * 장바구니 기반 주문 생성
     * 장바구니 조회 → 재고 차감 → Order/Payment 생성
     *
     * @param userId 유저 ID
     * @return 생성된 Order
     * @throws BusinessException 장바구니가 없을 경우
     * @throws SoldOutException  재고 부족
     */
    private Order createOrder(String userId) {
        Cart cart = cartRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorType.EMPTY_CART, "no cart, userId=" + userId));

        List<OrderItem> orderItems = buildOrderItems(cart); // OrderItem 생성 및 재고 차감
        int total = orderItems.stream().mapToInt(OrderItem::getLinePrice).sum();

        // Order/Payment 저장
        Order order = Order.create(userId, orderItems, total);
        order.attachPayment(new Payment(order));
        orderRepository.save(order);

        log.info("주문 생성 완료 orderId={}, userId={}, total={}, items={}",
                order.getId(), userId, total, orderItems.size());

        return order;
    }

    /**
     * 장바구니 상품을 OrderItem 목록으로 변환하고 재고 차감
     * reduceStock()은 DB 레벨에서 stock >= requested 조건으로 atomic하게 차감
     * 재고 부족 시 SoldOutException 발생 → 호출부 트랜잭션 롤백으로 차감 복구
     *
     * @param cart 주문할 장바구니
     * @return 생성된 OrderItem 목록
     * @throws SoldOutException 재고 부족 시
     */
    private List<OrderItem> buildOrderItems(Cart cart) {
        List<Long> productIds = cart.getProductIds();
        Map<Long, Product> productMap = productRepository.findAllByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return productIds.stream()
                .map(productId -> {
                    int requested = cart.getQuantityBy(productId);

                    if (productRepository.reduceStock(productId, requested) == 0) {
                        throw new SoldOutException(ErrorType.NOT_ENOUGH_STOCK, productId, requested);
                    }

                    Product product = productMap.get(productId);
                    return new OrderItem(product, requested, product.getPrice());
                })
                .toList();
    }

    /**
     * 결제 요청 커맨드를 Outbox에 적재
     *
     * @param order    생성된 주문
     * @param scenario PG 호출 시나리오
     * @param order
     * @param scenario
     */
    private void savePaymentCommandToOutbox(Order order, PgScenario scenario) {
        PaymentRequestedCommand cmd = PaymentRequestedCommand.from(order, scenario);
        String traceId = Span.current().getSpanContext().getTraceId();

        EventEnvelope<PaymentRequestedCommand> envelope
                = EventEnvelope.of(traceId, KafkaTopics.PAYMENT_COMMAND_REQUESTED, cmd);
        String key = String.valueOf(cmd.userId());

        outboxService.save(KafkaTopics.PAYMENT_COMMAND_REQUESTED, key, envelope);

        // 이후 infra.kafka.consumer.PaymentCommandConsumer에서 수신해 결제 처리
    }
}

