package com.example.edps.domain.cart.controller;

import com.example.edps.domain.cart.dto.CartResponse;
import com.example.edps.domain.cart.dto.UpsertCartItemRequest;
import com.example.edps.domain.cart.service.CartService;
import com.example.edps.global.common.AppHeaders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 인증/인가 로직은 범위에서 제외.
 * X-USER-ID 헤더로 사용자 식별
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/cart")
@Validated
public class CartController {
    private final CartService cartService;

    /**
     * 장바구니 조회
     * - 품절 상품은 목록에 표시되지만, 총합 금액에서는 제외
     */
    @GetMapping
    public ResponseEntity<CartResponse> getCart(@RequestHeader(AppHeaders.USER_ID) String userId) {
        return ResponseEntity.ok(cartService.get(userId));
    }

    /**
     * 장바구니 수량 절대값 설정(Upsert)
     * quantity는 1 이상만 허용 (@Min(1))
     */
    @PutMapping("/items/{id}")
    public ResponseEntity<CartResponse> upsertItem(
            @RequestHeader(AppHeaders.USER_ID) String userId,
            @PathVariable("id") Long productId,
            @RequestBody @Valid UpsertCartItemRequest request
    ) {
        return ResponseEntity.ok(cartService.upsertItem(userId, productId, request.quantity()));
    }

    /**
     * 장바구니에서 상품 제거
     */
    @DeleteMapping("/items/{id}")
    public ResponseEntity<CartResponse> removeItem(
            @RequestHeader(AppHeaders.USER_ID) String userId,
            @PathVariable("id") Long productId
    ) {
        return ResponseEntity.ok(cartService.removeItem(userId, productId));
    }
}

