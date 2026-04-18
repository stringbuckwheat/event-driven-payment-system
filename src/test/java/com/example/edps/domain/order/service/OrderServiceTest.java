package com.example.edps.domain.order.service;

import com.example.edps.domain.cart.model.Cart;
import com.example.edps.domain.cart.repository.CartRepository;
import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.entity.OrderItem;
import com.example.edps.domain.order.enums.PgScenario;
import com.example.edps.domain.order.repository.OrderRepository;
import com.example.edps.domain.product.entity.Product;
import com.example.edps.domain.product.repository.ProductRepository;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.BusinessException;
import com.example.edps.global.error.exception.SoldOutException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * OrderService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OutboxService outboxService;

    @InjectMocks
    private OrderService orderService;

    private static final String USER_ID = "user-1";

    // ===== 장바구니 검증 =====

    @Test
    @DisplayName("장바구니가 없으면 BusinessException(EMPTY_CART)")
    void order_throws_when_cart_not_found() {
        // given
        given(cartRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.order(USER_ID, PgScenario.SUCCESS))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorType())
                        .isEqualTo(ErrorType.EMPTY_CART));

        then(orderRepository).should(never()).save(any());
        then(outboxService).should(never()).save(any(), any(), any());
    }

    // ===== 재고 검증 =====

    @Test
    @DisplayName("재고 부족 시 SoldOutException, 주문 저장 X")
    void order_throws_sold_out_exception_when_stock_is_insufficient() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 3);

        Product product = createProduct(1L, 10_000, 2); // stock=2, requested=3

        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findAllByIdIn(any())).willReturn(List.of(product));
        given(productRepository.reduceStock(1L, 3)).willReturn(0); // 재고 부족

        // when & then
        assertThatThrownBy(() -> orderService.order(USER_ID, PgScenario.SUCCESS))
                .isInstanceOf(SoldOutException.class)
                .satisfies(e -> {
                    SoldOutException ex = (SoldOutException) e;
                    assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_ENOUGH_STOCK);
                    assertThat(ex.getProductId()).isEqualTo(1L);
                    assertThat(ex.getRequested()).isEqualTo(3);
                });

        then(orderRepository).should(never()).save(any());
        then(outboxService).should(never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("첫 번째 상품은 성공, 두 번째 상품이 재고 부족이면 SoldOutException이 발생, 주문 저장 X")
    void order_throws_when_second_product_is_sold_out_and_does_not_save_order() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 1);
        cart.updateItem(2L, 5);

        Product product1 = createProduct(1L, 10_000, 10);
        Product product2 = createProduct(2L, 5_000, 3); // stock=3, requested=5

        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findAllByIdIn(any())).willReturn(List.of(product1, product2));
        given(productRepository.reduceStock(eq(1L), anyInt())).willReturn(1);
        given(productRepository.reduceStock(eq(2L), anyInt())).willReturn(0); // 재고 부족

        // when & then
        assertThatThrownBy(() -> orderService.order(USER_ID, PgScenario.SUCCESS))
                .isInstanceOf(SoldOutException.class)
                .satisfies(e -> assertThat(((SoldOutException) e).getProductId()).isEqualTo(2L));

        then(orderRepository).should(never()).save(any());
    }

    // ===== 정상 케이스 =====

    @Test
    @DisplayName("정상 주문 테스트")
    void order_saves_order_on_success() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 2);

        Product product = createProduct(1L, 10_000, 10);

        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findAllByIdIn(any())).willReturn(List.of(product));
        given(productRepository.reduceStock(anyLong(), anyInt())).willReturn(1);

        // when
        orderService.order(USER_ID, PgScenario.SUCCESS);

        // then
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        then(orderRepository).should().save(captor.capture());

        Order saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getTotal()).isEqualTo(20_000);
        assertThat(saved.getOrderItems()).hasSize(1);
        assertThat(saved.getPayment()).isNotNull();

        // outbox 저장
        then(outboxService).should().save(
                eq(KafkaTopics.PAYMENT_COMMAND_REQUESTED),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("총액 테스트")
    void order_total_equals_sum_of_line_prices() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 2); // 10,000 * 2 = 20,000
        cart.updateItem(2L, 3); // 5,000 * 3 = 15,000

        Product product1 = createProduct(1L, 10_000, 10);
        Product product2 = createProduct(2L, 5_000, 10);

        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findAllByIdIn(any())).willReturn(List.of(product1, product2));
        given(productRepository.reduceStock(anyLong(), anyInt())).willReturn(1);

        // when
        orderService.order(USER_ID, PgScenario.SUCCESS);

        // then
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        then(orderRepository).should().save(captor.capture());

        assertThat(captor.getValue().getTotal()).isEqualTo(35_000);
    }

    @Test
    @DisplayName("OrderItem의 unitprice는 주문 시점의 스냅샷")
    void order_item_captures_price_at_order_time() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 2);

        Product product = createProduct(1L, 12_000, 10);

        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findAllByIdIn(any())).willReturn(List.of(product));
        given(productRepository.reduceStock(anyLong(), anyInt())).willReturn(1);

        // when
        orderService.order(USER_ID, PgScenario.SUCCESS);

        // then
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        then(orderRepository).should().save(captor.capture());

        OrderItem item = captor.getValue().getOrderItems().get(0);
        assertThat(item.getUnitPrice()).isEqualTo(12_000);
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getLinePrice()).isEqualTo(24_000);
    }

    // ===== Helper =====

    private Product createProduct(Long id, int price, int stock) {
        Product product = new Product("테스트상품", price, stock);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}