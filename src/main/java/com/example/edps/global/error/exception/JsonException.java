package com.example.edps.global.error.exception;

import com.example.edps.global.error.ErrorType;

public class JsonException extends RuntimeException {
    private final ErrorType errorType;

    public JsonException(ErrorType errorType, String detail) {
        super("%s - %s".formatted(errorType.getUserMessage(), detail));
        this.errorType = errorType;
    }

    public JsonException(ErrorType errorType, String detail, Throwable cause) {
        super("%s - %s".formatted(errorType.getUserMessage(), detail), cause);
        this.errorType = errorType;
    }
}
