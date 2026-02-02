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
import org.springframework.transaction.annotation.Transactional;
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

    @Transactional(readOnly = true)
    public CartResponse get(String userId) {
        // 카트 생성 시점: 최소 1개의 상품이 있을 떄
        Cart cart = cartRepository.findById(userId).orElse(null);

        // 카트나 상품 없으면 빈 dto 리턴
        if (cart == null || CollectionUtils.isEmpty(cart.getItems())) {
            return CartResponse.empty(userId);
        }

        return createCartResponse(cart);
    }

    /**
     * 수량 절대값으로 설정
     */
    @Transactional
    public CartResponse upsertItem(String userId, Long productId, int quantity) {
        Cart cart = cartRepository.findById(userId).orElse(null);

        // quantity <= 0이면 삭제
        if (quantity <= 0) {
            if (cart == null) return CartResponse.empty(userId);

            cart.removeItem(productId);
            if (deleteCartIfEmpty(cart)) return CartResponse.empty(userId);

            cartRepository.save(cart);
            return createCartResponse(cart);
        }

        // cart 없으면 생성
        if (cart == null) cart = new Cart(userId);

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
    @Transactional
    public CartResponse removeItem(String userId, Long productId) {
        Cart cart = cartRepository.findById(userId).orElse(null);

        if (cart == null) {
            return CartResponse.empty(userId);
        }

        cart.removeItem(productId);

        // 아이템 없으면 카트 삭제
        if (deleteCartIfEmpty(cart)) {
            return CartResponse.empty(userId);
        }

        cartRepository.save(cart);
        return createCartResponse(cart);
    }

    /**
     * 카트 응답 DTO 생성
     *
     * @param cart
     * @return
     */
    private CartResponse createCartResponse(Cart cart) {
        // 아이템 없으면 빈 응답
        if (CollectionUtils.isEmpty(cart.getItems())) {
            return CartResponse.empty(cart.getUserId());
        }

        // productId로 상품 조회해서 응답 구성
        List<Long> productIds = new ArrayList<>(cart.getItems().keySet());
        List<Product> products = productRepository.findAllByIdIn(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        int total = 0;
        List<CartItemResponse> items = new ArrayList<>(cart.getItems().size());

        for (Map.Entry<Long, Integer> entry : cart.getItems().entrySet()) {
            Long productId = entry.getKey();
            Integer quantity = entry.getValue();

            Product product = productMap.get(productId);
            if (product == null) continue; // 삭제 상품은 제외

            // 품절은 표시하되 총합에서만 제외
            if (!product.isSoldOut()) {
                total += product.getPrice() * quantity;
            }

            items.add(new CartItemResponse(product, quantity));
        }

        if (items.isEmpty()) return CartResponse.empty(cart.getUserId());
        return new CartResponse(cart.getUserId(), items, total);
    }

    private boolean deleteCartIfEmpty(Cart cart) {
        if (CollectionUtils.isEmpty(cart.getItems())) {
            cartRepository.deleteById(cart.getUserId());
            return true;
        }

        return false;
    }
}