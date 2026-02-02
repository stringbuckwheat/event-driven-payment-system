package com.example.edps.global.error;

import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        Map<String, Object> details
) {
    // enum에 저장된 오류 메시지 사용
    public ErrorResponse(ErrorType errorType) {
        this(errorType.name(), errorType.getUserMessage(), null);
    }

    // 유저 오류 메시지 커스텀 필요한 경우
    public ErrorResponse(ErrorType errorType, String userMessage) {
        this(errorType.name(), userMessage, null);
    }

    public ErrorResponse(ErrorType errorType, Map<String, Object> details) {
        this(errorType.name(), errorType.getUserMessage(), details);
    }
}

