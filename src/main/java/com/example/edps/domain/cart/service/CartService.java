package com.example.edps.domain.cart.service;

import com.example.edps.domain.cart.dto.CartItemResponse;
import com.example.edps.domain.cart.dto.CartResponse;
import com.example.edps.domain.cart.model.Cart;
import com.example.edps.domain.cart.repository.CartRepository;
import com.example.edps.domain.product.entity.Product;
import com.example.edps.domain.product.repository.ProductRepository;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.ElementNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public CartResponse get(String userId) {
        return cartRepository.findById(userId)
                .filter(c -> !CollectionUtils.isEmpty(c.getItems()))
                .map(this::createCartResponse)
                .orElseGet(() -> CartResponse.empty(userId));
    }

    /**
     * 수량 절대값으로 설정
     */
    public CartResponse upsertItem(String userId, Long productId, int quantity) {
        Cart cart = cartRepository.findById(userId).orElseGet(() -> new Cart(userId));

        if (!productRepository.existsById(productId)) {
            throw new ElementNotFoundException(ErrorType.PRODUCT_NOT_FOUND, "productId=" + productId);
        }

        cart.updateItem(productId, quantity);
        cartRepository.save(cart);

        return createCartResponse(cart);
    }

    /**
     * 상품 삭제
     *
     * @param userId    유저아이디
     * @param productId 상품PK
     * @return 변경된 장바구니의 현재 상태
     */
    public CartResponse removeItem(String userId, Long productId) {
        return cartRepository.findById(userId)
                .map(cart -> {
                    cart.removeItem(productId);
                    if(deleteCartIfEmpty(cart)) return CartResponse.empty(userId);
                    cartRepository.save(cart);
                    return createCartResponse(cart);
                })
                .orElseGet(() -> CartResponse.empty(userId));
    }

    /**
     * 카트 응답 DTO 생성
     *
     * @param cart 대상 장바구니
     */
    private CartResponse createCartResponse(Cart cart) {
        List<Long> productIds = new ArrayList<>(cart.getItems().keySet());
        Map<Long, Product> productMap = productRepository.findAllByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // 좀비 데이터 삭제
        removeDeletedProducts(cart, productMap);

        // 아이템 없으면 빈 카트 응답
        if (cart.getItems().isEmpty()) {
            return CartResponse.empty(cart.getUserId());
        }

        // 카트 내 상품 DTO
        List<CartItemResponse> items = cart.getItems().entrySet().stream()
                .filter(e -> productMap.containsKey(e.getKey()))
                .map(e -> CartItemResponse.of(productMap.get(e.getKey()), e.getValue()))
                .toList();

        // 총액
        int total = items.stream()
                .filter(item -> !item.soldOut())
                .mapToInt(CartItemResponse::linePrice)
                .sum();

        return new CartResponse(cart.getUserId(), items, total);
    }

    /**
     * 장바구니에서 삭제된 상품 제거
     * DB에 존재하지 않는 상품을 장바구니에서 제거하고, 장바구니가 비었으면 삭제
     * @param cart 정리 대상 장바구니
     * @param productMap db에서조회한 상품 map
     */
    private void removeDeletedProducts(Cart cart, Map<Long, Product> productMap) {
        List<Long> deletedIds = cart.getItems().keySet().stream()
                .filter(id -> !productMap.containsKey(id))
                .toList();

        if (deletedIds.isEmpty()) return;

        deletedIds.forEach(cart::removeItem);
        if (!deleteCartIfEmpty(cart)) cartRepository.save(cart);
    }

    private boolean deleteCartIfEmpty(Cart cart) {
        if (CollectionUtils.isEmpty(cart.getItems())) {
            cartRepository.deleteById(cart.getUserId());
            return true;
        }

        return false;
    }
}