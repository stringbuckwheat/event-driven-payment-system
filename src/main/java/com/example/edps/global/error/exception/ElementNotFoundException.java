package com.example.edps.global.error.exception;

import com.example.edps.global.error.ErrorType;
import lombok.Getter;

@Getter
public class ElementNotFoundException extends RuntimeException {
    private final ErrorType errorType;

    public ElementNotFoundException(ErrorType errorType, String detail) {
        super("%s - %s".formatted(errorType.getUserMessage(), detail));
        this.errorType = errorType;
    }

    public ElementNotFoundException(ErrorType errorType, String detail, Throwable cause) {
        super("%s - %s".formatted(errorType.getUserMessage(), detail), cause);
        this.errorType = errorType;
    }
}
