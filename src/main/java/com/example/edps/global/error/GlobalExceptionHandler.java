package com.example.edps.global.error;

import com.example.edps.global.error.exception.ElementNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ElementNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleElementNotFoundException(ElementNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        e.getErrorType(),
                        Map.of("detail", e.getMessage())
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();

        // @NotBlank 등의 어노테이션의 직접 작성된 메시지 가져옴
        String errorMessage = (fieldError != null && StringUtils.hasText(fieldError.getDefaultMessage()))
                ? fieldError.getDefaultMessage()
                : ErrorType.BAD_PARAMETER.getUserMessage();

        ErrorResponse errorResponse = new ErrorResponse(ErrorType.BAD_PARAMETER, errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ErrorType.UNKNOWN));
    }
}
