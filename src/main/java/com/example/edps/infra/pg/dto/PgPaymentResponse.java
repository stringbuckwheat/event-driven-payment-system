package com.example.edps.infra.pg.dto;

public record PgPaymentResponse(
        String result, // "SUCCESS" / "FAIL"
        String pgTxId, // 성공 시 세팅
        String reason  // 실패 사유
) {
    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(result);
    }
}
