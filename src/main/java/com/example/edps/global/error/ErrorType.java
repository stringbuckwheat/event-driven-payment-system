package com.example.edps.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ErrorType {
    // NOT FOUND
    PRODUCT_NOT_FOUND("상품을 찾을 수 없어요"),

    BAD_PARAMETER("요청 값이 올바르지 않습니다"),

    UNKNOWN("일시적인 오류가 발생했어요.");

    private String userMessage;
}
