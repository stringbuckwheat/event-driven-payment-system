package com.example.edps.domain.product.dto;

import com.example.edps.domain.product.entity.Product;

public record ProductResponse (
        Long id,
        String name,
        int price,
        boolean soldOut
){
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.isSoldOut()
        );
    }
}
