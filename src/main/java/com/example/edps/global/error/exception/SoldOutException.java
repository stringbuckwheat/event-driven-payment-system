package com.example.edps.global.error.exception;

import com.example.edps.global.error.ErrorType;
import lombok.Getter;

@Getter
public class SoldOutException extends BusinessException {
    private final Long productId;
    private final int requested;

    public SoldOutException(ErrorType errorType, Long productId, int requested, String detail) {
        super(errorType, detail);
        this.productId = productId;
        this.requested = requested;
    }

    public SoldOutException(ErrorType errorType, Long productId, int requested) {
        this(errorType, productId, requested, null);
    }
}