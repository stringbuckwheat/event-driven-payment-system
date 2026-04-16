package com.example.edps.domain.cart.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartTest {

    private static final String USER_ID = "user-1";

    @Test
    @DisplayName("새로 생성된 장바구니는 emtpy")
    void new_cart_is_empty() {
        // given
        Cart cart = new Cart(USER_ID);

        // then
        assertThat(cart.isEmpty()).isTrue();
        assertThat(cart.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("상품을 추가하면 장바구니는 not emtpy")
    void update_item_makes_cart_non_empty() {
        // given
        Cart cart = new Cart(USER_ID);

        // when
        cart.updateItem(1L, 3);

        // then
        assertThat(cart.isEmpty()).isFalse();
        assertThat(cart.getQuantityBy(1L)).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 상품을 다시 추가하면 수량이 덮어쓰기(증가 x)")
    void update_item_overwrites_quantity_not_increments() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 3);

        // when
        cart.updateItem(1L, 5);

        // then
        assertThat(cart.getQuantityBy(1L)).isEqualTo(5);
    }

    @Test
    @DisplayName("상품을 제거하면 해당 상품이 장바구니에서 사라진다")
    void remove_item_removes_product_from_cart() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 2);
        cart.updateItem(2L, 1);

        // when
        cart.removeItem(1L);

        // then
        assertThat(cart.getQuantityBy(1L)).isZero();
        assertThat(cart.getQuantityBy(2L)).isEqualTo(1);
        assertThat(cart.getProductIds()).containsExactly(2L);
    }

    @Test
    @DisplayName("유일한 상품을 제거하면 장바구니 empty")
    void remove_last_item_makes_cart_empty() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 1);

        // when
        cart.removeItem(1L);

        // then
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("getProductIds는 수정 불가능한 복사본 반환")
    void get_product_ids_returns_independent_copy() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 1);
        cart.updateItem(2L, 2);

        // when
        List<Long> ids = cart.getProductIds();

        // then
        assertThat(ids).containsExactlyInAnyOrder(1L, 2L);
        assertThatThrownBy(() -> ids.add(3L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("존재하지 않는 상품의 수량 조회는 0을 반환한다")
    void get_quantity_by_non_existent_product_returns_zero() {
        // given
        Cart cart = new Cart(USER_ID);

        // when & then
        assertThat(cart.getQuantityBy(999L)).isZero();
    }

    @Test
    @DisplayName("getItems는 수정 불가능한 맵 반환")
    void get_items_returns_unmodifiable_map() {
        // given
        Cart cart = new Cart(USER_ID);
        cart.updateItem(1L, 2);

        // when
        Map<Long, Integer> items = cart.getItems();

        // then
        assertThat(items).containsEntry(1L, 2);
        assertThatThrownBy(() -> items.put(2L, 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}