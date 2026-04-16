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

        // quantity <= 0이면 삭제
        if (quantity <= 0) {
            cart.removeItem(productId);
            if (deleteCartIfEmpty(cart)) return CartResponse.empty(userId);
            cartRepository.save(cart);
            return createCartResponse(cart);
        }

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