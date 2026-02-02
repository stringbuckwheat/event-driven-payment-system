package com.example.edps.domain.cart.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.HashMap;
import java.util.Map;

// TODO save 후에 expire 갱신 추가 고려
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart {
    @Id
    private String userId;

    // productId, quantity
    private Map<Long, Integer> items = new HashMap<>();

    public Cart(String userId) {
        this.userId = userId;
    }

    public void updateItem(Long productId, int quantity) {
        items.put(productId, quantity);
    }

    public void removeItem(Long productId) {
        items.remove(productId);
    }
}
