package com.example.edps.domain.cart.dto;

import com.example.edps.domain.product.entity.Product;

public record CartItemResponse(
        Long productId,
        String name,
        int price,
        int quantity,
        int linePrice,
        boolean soldOut
) {
    public static CartItemResponse of(Product product, int quantity) {
        return new CartItemResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                quantity,
                product.getPrice() * quantity,
                product.isSoldOut()
        );
    }
}