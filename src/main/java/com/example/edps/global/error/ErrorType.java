package com.example.edps.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ErrorType {
    // NOT FOUND
    PRODUCT_NOT_FOUND("상품을 찾을 수 없어요"),
    OUTBOX_NOT_FOUND("주문 과정 중 문제 발생"),
    PAYMENT_NOT_FOUND("결제 내역을 찾을 수 없어요"),
    ORDER_NOT_FOUND("주문 내역을 찾을 수 없어요"),
    CART_NOT_FOUND("카트를 찾을 수 없어요"),

    // ACCESS DENIED
    ORDER_ACCESS_DENIED("주문을 확인할 권한이 없어요"),

    // 주문 생성
    EMPTY_CART("장바구니가 비었어요"),
    NOT_ENOUGH_STOCK("재고가 모자라요"),
    OUT_OF_STOCK("이 상품은 품절이에요"),

    BAD_PARAMETER("요청 값이 올바르지 않습니다"),

    // 결제 관련
    PAYMENT_IN_PROGRESS("이미 진행중인 결제가 있어요"),

    // JSON
    SERIALIZE_FAIL("직렬화 실패"),
    DESERIALIZE_FAIL("역직렬화 실패"),

    UNKNOWN("일시적인 오류가 발생했어요.");

    private String userMessage;
}
