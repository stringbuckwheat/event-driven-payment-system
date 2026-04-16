package com.example.edps.domain.cart.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RedisHash(value = "cart", timeToLive = 60 * 60 * 24 * 7) // 7일
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

    public List<Long> getProductIds() {
        return List.copyOf(items.keySet());
    }

    public int getQuantityBy(Long productId) {
        return items.getOrDefault(productId, 0);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Map<Long, Integer> getItems() {
        return Collections.unmodifiableMap(items);
    }
}
