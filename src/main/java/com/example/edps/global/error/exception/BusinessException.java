package com.example.edps.global.error.exception;

import com.example.edps.global.error.ErrorType;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorType errorType;
    private final String detail;

    public BusinessException(ErrorType errorType, String detail) {
        super(errorType.getUserMessage());
        this.errorType = errorType;
        this.detail = detail;
    }

    public BusinessException(ErrorType errorType) {
        this(errorType, null);
    }
}