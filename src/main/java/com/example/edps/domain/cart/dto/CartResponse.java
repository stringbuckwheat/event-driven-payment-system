package com.example.edps.domain.cart.dto;

import java.util.List;

public record CartResponse(
        String userId,
        List<CartItemResponse> items,
        int total
) {
    public static CartResponse empty(String userId) {
        return new CartResponse(userId, List.of(), 0);
    }
}

