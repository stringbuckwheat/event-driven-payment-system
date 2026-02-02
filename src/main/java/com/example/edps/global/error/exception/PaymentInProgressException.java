package com.example.edps.global.error.exception;

import com.example.edps.global.error.ErrorType;
import lombok.Getter;

@Getter
public class PaymentInProgressException extends RuntimeException {

    private final ErrorType errorType;

    public PaymentInProgressException() {
        super(ErrorType.PAYMENT_IN_PROGRESS.getUserMessage());
        this.errorType = ErrorType.PAYMENT_IN_PROGRESS;
    }
}
