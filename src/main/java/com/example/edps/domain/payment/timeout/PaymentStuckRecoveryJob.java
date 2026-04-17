package com.example.edps.domain.payment.timeout;

import com.example.edps.domain.order.enums.OrderStatus;
import com.example.edps.domain.order.repository.OrderRepository;
import com.example.edps.domain.payment.enums.PayStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStuckRecoveryJob {

    private final OrderRepository orderRepository;
    private final PaymentStuckRecoveryService recoveryService;

    private static final int PAGE_SIZE = 100;

    @Scheduled(fixedDelay = 60_000)
    public void recover() {
        log.info("[RECOVER] payment stuck recovery 시작");
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(PaymentStuckRecoveryService.STUCK_MINUTES);
        int totalRecovered = 0;

        while (true) {
            List<Long> ids = orderRepository.findStuckOrderIds(
                    PayStatus.PROCESSING, cutoff,
                    OrderStatus.PAID, OrderStatus.FAILED,
                    PageRequest.of(0, PAGE_SIZE));

            if (ids.isEmpty()) break;

            int recovered = 0;
            for (Long orderId : ids) {
                try {
                    recoveryService.recoverSingleOrder(orderId);
                    recovered++;
                } catch (Exception e) {
                    log.error("[RECOVER] 개별 복구 실패 orderId={}, cause={}", orderId, e.getMessage(), e);
                }
            }
            totalRecovered += recovered;
        }

        if (totalRecovered == 0) {
            log.info("[RECOVER] stuck 주문 없음");
        } else {
            log.info("[RECOVER] 복구 완료 total={}", totalRecovered);
        }
    }
}