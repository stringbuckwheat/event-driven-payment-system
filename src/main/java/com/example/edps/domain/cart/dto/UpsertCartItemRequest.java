package com.example.edps.domain.cart.dto;

import jakarta.validation.constraints.Min;

public record UpsertCartItemRequest(
        @Min(value = 1, message = "수량은 1 이상이어야 합니다")
        int quantity
) {
}
