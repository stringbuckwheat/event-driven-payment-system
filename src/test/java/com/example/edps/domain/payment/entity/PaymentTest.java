package com.example.edps.domain.payment.entity;

import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.payment.enums.PayStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payment 엔티티 단위 테스트
 */
class PaymentTest {

    private Order makeOrder(int total) {
        Order order = Order.create("user-1", Collections.emptyList(), total);
        return order;
    }

    @Test
    @DisplayName("Payment 생성 시 초기 상태는 READY, 총액은 Order와 동일")
    void initial_status_is_ready_with_correct_total() {
        Order order = makeOrder(30_000);

        Payment payment = new Payment(order);

        assertThat(payment.getStatus()).isEqualTo(PayStatus.READY);
        assertThat(payment.getTotal()).isEqualTo(30_000);
        assertThat(payment.getPgTxId()).isNull();
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("complete() 호출 시 SUCCESS 상태 및 pgTxId가 세팅된다")
    void complete_sets_success_status_and_pg_tx_id() {
        Payment payment = new Payment(makeOrder(10_000));
        LocalDateTime requestedAt = LocalDateTime.of(2026, 4, 17, 10, 0);
        LocalDateTime completedAt = LocalDateTime.of(2026, 4, 17, 10, 0, 1);

        payment.complete(PayStatus.SUCCESS, "pg-tx-001", requestedAt, completedAt, null);

        assertThat(payment.getStatus()).isEqualTo(PayStatus.SUCCESS);
        assertThat(payment.getPgTxId()).isEqualTo("pg-tx-001");
        assertThat(payment.getRequestedAt()).isEqualTo(requestedAt);
        assertThat(payment.getCompletedAt()).isEqualTo(completedAt);
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("complete() 호출 시 FAILED 상태이면 failureReason이 세팅되고 pgTxId는 null")
    void complete_sets_failed_status_with_failure_reason_and_no_pg_tx_id() {
        Payment payment = new Payment(makeOrder(10_000));
        LocalDateTime now = LocalDateTime.now();

        payment.complete(PayStatus.FAILED, null, now, now, "카드 한도 초과");

        assertThat(payment.getStatus()).isEqualTo(PayStatus.FAILED);
        assertThat(payment.getPgTxId()).isNull();
        assertThat(payment.getFailureReason()).isEqualTo("카드 한도 초과");
    }
}