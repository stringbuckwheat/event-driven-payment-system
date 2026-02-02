package com.example.edps.domain.cart.dto;

import jakarta.validation.constraints.Min;

public record UpsertCartItemRequest(
        @Min(value = 0, message = "수량을 확인해주세요")
        int quantity
) {
}
