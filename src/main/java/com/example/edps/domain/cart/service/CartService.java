package com.example.edps.domain.cart.service;

import com.example.edps.domain.cart.dto.CartItemResponse;
import com.example.edps.domain.cart.dto.CartResponse;
import com.example.edps.domain.cart.model.Cart;
import com.example.edps.domain.cart.repository.CartRepository;
import com.example.edps.domain.product.entity.Product;
import com.example.edps.domain.product.repository.ProductRepository;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    /**
     * 장바구니 조회
     * 조회 시점에 DB에서 삭제된 상품(좀비 데이터) 정리하므로 Redis write 발생 가능
     *
     * @param userId 유저ID
     * @return 장바구니 응답 DTO
     */
    public CartResponse get(String userId) {
        return cartRepository.findById(userId)
                .filter(cart -> !cart.isEmpty())
                .map(cart -> {
                    Map<Long, Product> productMap = fetchProductMap(cart);
                    removeDeletedProducts(cart, productMap);
                    if (cart.isEmpty()) return CartResponse.empty(userId);
                    return buildCartResponse(cart, productMap);
                })
                .orElseGet(() -> CartResponse.empty(userId));
    }

    /**
     * 장바구니 상품 추가/수량 변경
     * quantity는 절대값으로 설정 (@Min(1) 검증)
     *
     * @param userId    유저ID
     * @param productId 상품ID
     * @param quantity  수량
     * @return 변경된 장바구니 응답 DTO
     * @throws com.example.edps.global.error.exception.BusinessException 상품이 RDB에 존재하지 않을 경우
     */
    public CartResponse upsertItem(String userId, Long productId, int quantity) {
        Cart cart = cartRepository.findById(userId).orElseGet(() -> new Cart(userId));

        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorType.PRODUCT_NOT_FOUND, "productId=" + productId);
        }

        cart.updateItem(productId, quantity);
        cartRepository.save(cart);

        return buildCartResponse(cart, fetchProductMap(cart));
    }

    /**
     * 장바구니 상품 삭제
     *
     * @param userId    유저ID
     * @param productId 상품ID
     * @return 변경된 장바구니 응답
     */
    public CartResponse removeItem(String userId, Long productId) {
        return cartRepository.findById(userId)
                .map(cart -> {
                    cart.removeItem(productId);
                    if (deleteCartIfEmpty(cart)) return CartResponse.empty(userId);
                    cartRepository.save(cart);
                    return buildCartResponse(cart, fetchProductMap(cart));
                })
                .orElseGet(() -> CartResponse.empty(userId));
    }

    /**
     * 장바구니 내 상품 ID로 상품 map 조회
     *
     * @param cart 대상 장바구니
     * @return productId → Product Map
     */
    private Map<Long, Product> fetchProductMap(Cart cart) {
        return productRepository.findAllByIdIn(cart.getProductIds()).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
    }

    /**
     * 장바구니 응답 DTO 변환 + 총액 계산
     * (좀비 데이터 정리는 '장바구니 조회'에서만)
     *
     * @param cart       대상 장바구니
     * @param productMap DB에서 조회한 상품 Map (productId → Product)
     * @return 장바구니 응답 DTO
     */
    private CartResponse buildCartResponse(Cart cart, Map<Long, Product> productMap) {
        List<CartItemResponse> items = cart.getProductIds().stream()
                .filter(productMap::containsKey)
                .map(productId -> CartItemResponse.of(productMap.get(productId), cart.getQuantityBy(productId)))
                .toList();

        int total = items.stream()
                .filter(item -> !item.soldOut())
                .mapToInt(CartItemResponse::linePrice)
                .sum();

        return new CartResponse(cart.getUserId(), items, total);
    }

    /**
     * DB에 존재하지 않는 상품을 장바구니에서 제거
     * 좀비 제거 후 장바구니가 비면 장바구니 자체를 삭제
     *
     * @param cart       정리 대상 장바구니
     * @param productMap DB에서 조회한 상품 Map (productId → Product)
     */
    private void removeDeletedProducts(Cart cart, Map<Long, Product> productMap) {
        List<Long> deletedIds = cart.getProductIds().stream()
                .filter(id -> !productMap.containsKey(id))
                .toList();

        if (deletedIds.isEmpty()) return;

        deletedIds.forEach(cart::removeItem);
        if (!deleteCartIfEmpty(cart)) cartRepository.save(cart);
    }

    /**
     * 장바구니가 비어있으면 Redis에서 삭제
     *
     * @param cart 대상 장바구니
     * @return 삭제됐으면 true, 아이템이 남아있으면 false
     */
    private boolean deleteCartIfEmpty(Cart cart) {
        if (cart.isEmpty()) {
            cartRepository.deleteById(cart.getUserId());
            return true;
        }
        return false;
    }
}