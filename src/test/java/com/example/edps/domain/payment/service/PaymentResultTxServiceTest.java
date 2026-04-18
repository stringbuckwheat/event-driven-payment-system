package com.example.edps.domain.payment.service;

import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.entity.OrderItem;
import com.example.edps.domain.order.repository.OrderRepository;
import com.example.edps.domain.cart.repository.CartRepository;
import com.example.edps.domain.order.enums.OrderStatus;
import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.event.PaymentCompletedEvent;
import com.example.edps.domain.product.entity.Product;
import com.example.edps.domain.product.repository.ProductRepository;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.BusinessException;
import com.example.edps.infra.processedevent.ProcessedEvent;
import com.example.edps.infra.processedevent.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * PaymentResultTxService 단위 테스트
 *
 * 검증 대상:
 * - applySuccess(): 멱등성 중복 스킵, 주문 PAID 확정, 장바구니 삭제, ProcessedEvent 저장
 * - applyFailure(): 멱등성 중복 스킵, 주문 FAILED 확정, 재고 롤백, ProcessedEvent 저장
 */
@ExtendWith(MockitoExtension.class)
class PaymentResultTxServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProcessedEventRepository processedEventRepository;

    @InjectMocks private PaymentResultTxService paymentResultTxService;

    private static final String EVENT_ID = "evt-abc-123";
    private static final long ORDER_ID = 100L;
    private static final long PAYMENT_ID = 1L;
    private static final String USER_ID = "user-1";

    // ===== applySuccess =====

    @Test
    @DisplayName("이미 처리된 eventId면 applySuccess()는 아무것도 하지 않는다")
    void applySuccess_skips_duplicate_event() {
        given(processedEventRepository.existsByEventId(EVENT_ID)).willReturn(true);

        paymentResultTxService.applySuccess(makeSuccessEvent(), EVENT_ID);

        then(orderRepository).shouldHaveNoInteractions();
        then(cartRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("applySuccess()는 주문을 PAID로 확정하고 장바구니를 삭제하며 ProcessedEvent를 저장한다")
    void applySuccess_marks_order_paid_clears_cart_and_saves_processed_event() {
        Order order = makeOrder();
        given(processedEventRepository.existsByEventId(EVENT_ID)).willReturn(false);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        paymentResultTxService.applySuccess(makeSuccessEvent(), EVENT_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        then(cartRepository).should().deleteById(USER_ID);

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        then(processedEventRepository).should().save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(EVENT_ID);
    }

    @Test
    @DisplayName("applySuccess() 시 주문이 없으면 ORDER_NOT_FOUND 예외")
    void applySuccess_throws_when_order_not_found() {
        given(processedEventRepository.existsByEventId(EVENT_ID)).willReturn(false);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentResultTxService.applySuccess(makeSuccessEvent(), EVENT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorType())
                        .isEqualTo(ErrorType.ORDER_NOT_FOUND));
    }

    // ===== applyFailure =====

    @Test
    @DisplayName("이미 처리된 eventId면 applyFailure()는 아무것도 하지 않는다")
    void applyFailure_skips_duplicate_event() {
        given(processedEventRepository.existsByEventId(EVENT_ID)).willReturn(true);

        paymentResultTxService.applyFailure(makeFailureEvent(), EVENT_ID);

        then(orderRepository).shouldHaveNoInteractions();
        then(productRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("applyFailure()는 주문을 FAILED로 확정하고 OrderItem별 재고를 롤백한다")
    void applyFailure_marks_order_failed_and_rolls_back_stock_per_item() {
        Product product1 = makeProduct(10L);
        Product product2 = makeProduct(20L);
        Order order = makeOrderWithItems(
                new OrderItem(product1, 2, 10_000),
                new OrderItem(product2, 3, 5_000)
        );

        given(processedEventRepository.existsByEventId(EVENT_ID)).willReturn(false);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        paymentResultTxService.applyFailure(makeFailureEvent(), EVENT_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        then(productRepository).should().increaseStock(10L, 2);
        then(productRepository).should().increaseStock(20L, 3);
    }

    @Test
    @DisplayName("applyFailure() 시 주문이 없으면 ORDER_NOT_FOUND 예외")
    void applyFailure_throws_when_order_not_found() {
        given(processedEventRepository.existsByEventId(EVENT_ID)).willReturn(false);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentResultTxService.applyFailure(makeFailureEvent(), EVENT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorType())
                        .isEqualTo(ErrorType.ORDER_NOT_FOUND));
    }

    @Test
    @DisplayName("applyFailure() 후 ProcessedEvent가 저장된다")
    void applyFailure_saves_processed_event() {
        Order order = makeOrder();
        given(processedEventRepository.existsByEventId(EVENT_ID)).willReturn(false);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        paymentResultTxService.applyFailure(makeFailureEvent(), EVENT_ID);

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        then(processedEventRepository).should().save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(EVENT_ID);
    }

    @Test
    @DisplayName("OrderItem이 없는 주문 실패 처리 시 재고 롤백을 호출하지 않는다")
    void applyFailure_does_not_call_increase_stock_when_order_has_no_items() {
        Order order = makeOrder(); // items 없음
        given(processedEventRepository.existsByEventId(EVENT_ID)).willReturn(false);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        paymentResultTxService.applyFailure(makeFailureEvent(), EVENT_ID);

        then(productRepository).should(never()).increaseStock(anyLong(), anyInt());
    }

    // ===== helpers =====

    private Order makeOrder() {
        Order order = Order.create(USER_ID, List.of(), 10_000);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }

    private Order makeOrderWithItems(OrderItem... items) {
        Order order = Order.create(USER_ID, List.of(items), 10_000);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }

    private Product makeProduct(long id) {
        Product p = new Product("상품", 10_000, 10);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private PaymentCompletedEvent makeSuccessEvent() {
        return PaymentCompletedEvent.builder()
                .orderId(ORDER_ID)
                .paymentId(PAYMENT_ID)
                .status(PayStatus.SUCCESS)
                .pgTxId("pg-tx-001")
                .requestedAt(LocalDateTime.now())
                .respondedAt(LocalDateTime.now())
                .attemptNo(1)
                .build();
    }

    private PaymentCompletedEvent makeFailureEvent() {
        return PaymentCompletedEvent.builder()
                .orderId(ORDER_ID)
                .paymentId(PAYMENT_ID)
                .status(PayStatus.FAILED)
                .failureReason("카드 한도 초과")
                .requestedAt(LocalDateTime.now())
                .respondedAt(LocalDateTime.now())
                .attemptNo(1)
                .build();
    }
}