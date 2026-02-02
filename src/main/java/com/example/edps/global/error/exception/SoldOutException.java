package com.example.edps.global.error.exception;

import com.example.edps.global.error.ErrorType;
import lombok.Getter;

@Getter
public class SoldOutException extends RuntimeException {
    private final ErrorType errorType;
    private Long productId; // 품절 제품 pk
    private int requested;

    public SoldOutException(ErrorType errorType, Long productId, int requested) {
        super();
        this.errorType = errorType;
        this.productId = productId;
        this.requested = requested;
    }

    public SoldOutException(ErrorType errorType, Long productId, int requested, String detail) {
        super("%s - %s".formatted(errorType.getUserMessage(), detail));
        this.errorType = errorType;
        this.productId = productId;
        this.requested = requested;
    }

    public SoldOutException(ErrorType errorType, Long productId, int requested, String detail, Throwable cause) {
        super("%s - %s".formatted(errorType.getUserMessage(), detail), cause);
        this.errorType = errorType;
        this.productId = productId;
        this.requested = requested;
    }
}
