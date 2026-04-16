package com.example.edps.domain.cart.service;

import com.example.edps.domain.cart.dto.CartItemResponse;
import com.example.edps.domain.cart.dto.CartResponse;
import com.example.edps.domain.cart.model.Cart;
import com.example.edps.domain.cart.repository.CartRepository;
import com.example.edps.domain.product.entity.Product;
import com.example.edps.domain.product.repository.ProductRepository;
import com.example.edps.global.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    private static final String USER_ID = "user-1";

    // ===== get() =====

    @Test
    @DisplayName("장바구니가 존재하지 않으면 빈 응답 반환")
    void get_returns_empty_when_cart_not_found() {
        // given
        given(cartRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when
        CartResponse response = cartService.get(USER_ID);

        // then
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
        then(productRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("장바구니가 비어있으면(items empty) 빈 응답 반환")
    void get_returns_empty_when_cart_exists_but_empty() {
        // given
        Cart cart = new Cart(USER_ID);
        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));

        // when
        CartResponse response = cartService.get(USER_ID);

        // then
        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
        then(productRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("정상 장바구니 조회 시 상품 정보와 총액 반환")
    void get_returns_cart_with_products_and_total() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 2);
        cart.updateItem(2L, 1);

        Product product1 = createProduct(1L, "상품A", 10_000, 10);
        Product product2 = createProduct(2L, "상품B", 5_000, 5);

        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findAllByIdIn(any())).willReturn(List.of(product1, product2));

        // when
        CartResponse response = cartService.get(USER_ID);

        // then
        assertThat(response.items()).hasSize(2);
        assertThat(response.total()).isEqualTo(10_000 * 2 + 5_000 * 1);
    }

    @Test
    @DisplayName("일부 상품이 DB에서 삭제된 경우(좀비) 해당 상품을 장바구니에서 제거하고 나머지를 반환")
    void get_removes_zombie_products_and_returns_remaining() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 2);
        cart.updateItem(2L, 1); // zombie
        cart.updateItem(3L, 3);

        Product product1 = createProduct(1L, "상품A", 10_000, 10);
        Product product3 = createProduct(3L, "상품C", 3_000, 7);

        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findAllByIdIn(any())).willReturn(List.of(product1, product3));

        // when
        CartResponse response = cartService.get(USER_ID);

        // then
        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting(CartItemResponse::productId)
                .containsExactlyInAnyOrder(1L, 3L);
        assertThat(response.total()).isEqualTo(10_000 * 2 + 3_000 * 3);
        then(cartRepository).should().save(cart);
    }

    @Test
    @DisplayName("모든 상품이 DB에서 삭제된 경우 장바구니를 삭제하고 빈 응답을 반환")
    void get_deletes_cart_when_all_products_are_zombies() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 2);
        cart.updateItem(2L, 1);

        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findAllByIdIn(any())).willReturn(List.of());

        // when
        CartResponse response = cartService.get(USER_ID);

        // then
        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
        then(cartRepository).should().deleteById(USER_ID);
        then(cartRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("품절 상품은 목록에 포함되지만 총액에서는 제외")
    void get_includes_sold_out_items_in_list_but_excludes_from_total() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 2);
        cart.updateItem(2L, 1);

        Product product1 = createProduct(1L, "상품A", 10_000, 10);
        Product soldOut = createProduct(2L, "품절상품", 5_000, 0);

        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findAllByIdIn(any())).willReturn(List.of(product1, soldOut));

        // when
        CartResponse response = cartService.get(USER_ID);

        // then
        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).anyMatch(CartItemResponse::soldOut);
        assertThat(response.total()).isEqualTo(10_000 * 2);
    }

    @Test
    @DisplayName("좀비 상품과 품절 상품이 동시에 존재하는 경우 정상 처리한다")
    void get_handles_zombie_and_sold_out_together() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 2); // normal
        cart.updateItem(2L, 1); // zombie
        cart.updateItem(3L, 3); // sold out

        Product product1 = createProduct(1L, "정상상품", 10_000, 10);
        Product soldOut = createProduct(3L, "품절상품", 3_000, 0);

        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));
        given(productRepository.findAllByIdIn(any())).willReturn(List.of(product1, soldOut));

        // when
        CartResponse response = cartService.get(USER_ID);

        // then
        assertThat(response.items()).hasSize(2);
        assertThat(response.total()).isEqualTo(10_000 * 2);
        then(cartRepository).should().save(cart);
    }

    // ===== upsertItem() =====

    @Test
    @DisplayName("장바구니가 없을 때 상품을 추가하면 새 장바구니 생성")
    void upsert_item_creates_new_cart_when_not_exists() {
        // given
        given(cartRepository.findById(USER_ID)).willReturn(Optional.empty());
        given(productRepository.existsById(1L)).willReturn(true);
        given(cartRepository.save(any(Cart.class))).willAnswer(inv -> inv.getArgument(0));

        Product product = createProduct(1L, "상품A", 10_000, 10);
        given(productRepository.findAllByIdIn(any())).willReturn(List.of(product));

        // when
        CartResponse response = cartService.upsertItem(USER_ID, 1L, 3);

        // then
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(3);
        then(cartRepository).should().save(any(Cart.class));
    }

    @Test
    @DisplayName("기존 장바구니에 상품을 추가하면 수량 덮어쓰기")
    void upsert_item_overwrites_quantity_on_existing_cart() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 2);

        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));
        given(productRepository.existsById(1L)).willReturn(true);
        given(cartRepository.save(any(Cart.class))).willAnswer(inv -> inv.getArgument(0));

        Product product = createProduct(1L, "상품A", 10_000, 10);
        given(productRepository.findAllByIdIn(any())).willReturn(List.of(product));

        // when
        CartResponse response = cartService.upsertItem(USER_ID, 1L, 5);

        // then
        assertThat(response.items().get(0).quantity()).isEqualTo(5);
        assertThat(response.total()).isEqualTo(10_000 * 5);
    }

    @Test
    @DisplayName("존재하지 않는 상품을 추가하면 ElementNotFoundException")
    void upsert_item_throws_when_product_not_found() {
        // given
        given(cartRepository.findById(USER_ID)).willReturn(Optional.empty());
        given(productRepository.existsById(999L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> cartService.upsertItem(USER_ID, 999L, 1))
                .isInstanceOf(BusinessException.class);
        then(cartRepository).should(never()).save(any());
    }

    // ===== removeItem() =====

    @Test
    @DisplayName("장바구니에서 상품을 제거하면 남은 상품으로 응답")
    void remove_item_returns_remaining_items() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 2);
        cart.updateItem(2L, 1);

        Product product2 = createProduct(2L, "상품B", 5_000, 5);

        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));
        given(cartRepository.save(any(Cart.class))).willAnswer(inv -> inv.getArgument(0));
        given(productRepository.findAllByIdIn(any())).willReturn(List.of(product2));

        // when
        CartResponse response = cartService.removeItem(USER_ID, 1L);

        // then
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo(2L);
        then(cartRepository).should().save(cart);
        then(cartRepository).should(never()).deleteById(any());
    }

    @Test
    @DisplayName("마지막 상품을 제거하면 장바구니 Redis에서 삭제")
    void remove_last_item_deletes_cart_from_redis() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 1);

        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));

        // when
        CartResponse response = cartService.removeItem(USER_ID, 1L);

        // then
        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
        then(cartRepository).should().deleteById(USER_ID);
        then(cartRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("장바구니에 없는 상품을 제거해도 나머지 상품은 정상 반환")
    void remove_non_existent_item_returns_cart_unchanged() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 2);

        Product product1 = createProduct(1L, "상품A", 10_000, 10);

        given(cartRepository.findById(USER_ID)).willReturn(Optional.of(cart));
        given(cartRepository.save(any(Cart.class))).willAnswer(inv -> inv.getArgument(0));
        given(productRepository.findAllByIdIn(any())).willReturn(List.of(product1));

        // when
        CartResponse response = cartService.removeItem(USER_ID, 999L);

        // then
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo(1L);
        then(cartRepository).should().save(cart);
    }

    // ===== Helper =====

    private Product createProduct(Long id, String name, int price, int stock) {
        Product product = new Product(name, price, stock);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}