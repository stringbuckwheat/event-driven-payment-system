package com.example.edps.domain.cart.dto;

import com.example.edps.domain.product.entity.Product;
import lombok.Getter;

@Getter
public class CartItemResponse {
    private Long productId;

    private int quantity;
    private String name;
    private int price;
    private int linePrice;
    private boolean soldOut;

    public CartItemResponse(Product product, int quantity) {
        this.productId = product.getId();
        this.quantity = quantity;
        this.name = product.getName();
        this.price = product.getPrice();
        this.linePrice = product.getPrice() * quantity;
        this.soldOut = product.isSoldOut();
    }
}