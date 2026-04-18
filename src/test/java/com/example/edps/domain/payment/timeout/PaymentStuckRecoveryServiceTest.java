package com.example.edps.domain.payment.timeout;

import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.entity.OrderItem;
import com.example.edps.domain.order.enums.OrderStatus;
import com.example.edps.domain.order.repository.OrderRepository;
import com.example.edps.domain.payment.entity.Payment;
import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.repository.PaymentRepository;
import com.example.edps.domain.product.entity.Product;
import com.example.edps.domain.product.repository.ProductRepository;
import com.example.edps.infra.kafka.KafkaTopics;
import com.example.edps.infra.outbox.service.OutboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * PaymentStuckRecoveryService 단위 테스트
 *
 * 검증 대상:
 * - transitionStatus가 0이면(다른 워커가 선점) 아무것도 하지 않는다
 * - 정상 복구: FAILED 확정 + 주문 실패 + 재고 롤백 + failed 토픽 Outbox
 */
@ExtendWith(MockitoExtension.class)
class PaymentStuckRecoveryServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OutboxService outboxService;

    @InjectMocks private PaymentStuckRecoveryService recoveryService;

    private static final long ORDER_ID = 100L;
    private static final long PAYMENT_ID = 1L;

    @Test
    @DisplayName("다른 워커가 이미 처리한 결제(transitionStatus=0)는 복구를 건너뛴다")
    void skips_recovery_when_already_processed_by_another_worker() {
        Order order = makeOrder(List.of());
        given(orderRepository.findOrderWithItemsById(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentRepository.transitionStatus(PAYMENT_ID, PayStatus.PROCESSING, PayStatus.FAILED))
                .willReturn(0);

        recoveryService.recoverSingleOrder(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED); // 변경 없음
        then(productRepository).shouldHaveNoInteractions();
        then(outboxService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("stuck 결제 복구: 주문을 FAILED로 확정하고 재고를 롤백하며 failed 토픽으로 이벤트를 발행한다")
    void recovers_stuck_payment_with_failed_status_stock_rollback_and_outbox() {
        Product product = new Product("상품", 10_000, 5);
        ReflectionTestUtils.setField(product, "id", 10L);

        Order order = makeOrder(List.of(new OrderItem(product, 3, 10_000)));
        given(orderRepository.findOrderWithItemsById(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentRepository.transitionStatus(PAYMENT_ID, PayStatus.PROCESSING, PayStatus.FAILED))
                .willReturn(1);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);

        recoveryService.recoverSingleOrder(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        then(productRepository).should().increaseStock(10L, 3);
        then(outboxService).should().save(topicCaptor.capture(), any(), any());
        assertThat(topicCaptor.getValue()).isEqualTo(KafkaTopics.PAYMENT_EVENT_FAILED);
    }

    @Test
    @DisplayName("OrderItem이 여러 개일 때 각 상품의 수량만큼 재고를 롤백한다")
    void recovers_stock_for_each_order_item() {
        Product p1 = new Product("A", 10_000, 5);
        Product p2 = new Product("B", 5_000, 10);
        ReflectionTestUtils.setField(p1, "id", 10L);
        ReflectionTestUtils.setField(p2, "id", 20L);

        Order order = makeOrder(List.of(
                new OrderItem(p1, 2, 10_000),
                new OrderItem(p2, 4, 5_000)
        ));
        given(orderRepository.findOrderWithItemsById(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentRepository.transitionStatus(PAYMENT_ID, PayStatus.PROCESSING, PayStatus.FAILED))
                .willReturn(1);

        recoveryService.recoverSingleOrder(ORDER_ID);

        then(productRepository).should().increaseStock(10L, 2);
        then(productRepository).should().increaseStock(20L, 4);
    }

    @Test
    @DisplayName("OrderItem이 없는 주문도 재고 롤백 없이 정상 복구된다")
    void recovers_order_with_no_items_without_stock_rollback() {
        Order order = makeOrder(List.of());
        given(orderRepository.findOrderWithItemsById(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentRepository.transitionStatus(PAYMENT_ID, PayStatus.PROCESSING, PayStatus.FAILED))
                .willReturn(1);

        recoveryService.recoverSingleOrder(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        then(productRepository).should(never()).increaseStock(anyLong(), anyInt());
        then(outboxService).should().save(any(), any(), any());
    }

    @Test
    @DisplayName("OrderId를 찾을 수 없으면 IllegalStateException이 발생한다")
    void throws_when_order_not_found() {
        given(orderRepository.findOrderWithItemsById(ORDER_ID)).willReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> recoveryService.recoverSingleOrder(ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(ORDER_ID));
    }

    // ===== helpers =====

    private Order makeOrder(List<OrderItem> items) {
        Order order = Order.create("user-1", items, 10_000);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);

        Payment payment = new Payment(order);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        ReflectionTestUtils.setField(payment, "status", PayStatus.PROCESSING);
        ReflectionTestUtils.setField(order, "payment", payment);

        return order;
    }
}