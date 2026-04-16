package com.example.edps.domain.payment.service;

import com.example.edps.domain.cart.repository.CartRepository;
import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.enums.OrderStatus;
import com.example.edps.domain.order.repository.OrderRepository;
import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.event.PaymentCompletedEvent;
import com.example.edps.domain.product.repository.ProductRepository;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.BusinessException;
import com.example.edps.infra.idempotency.ProcessedEvent;
import com.example.edps.infra.idempotency.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentResultTxService {
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void applySuccess(PaymentCompletedEvent result, String eventId) {
        if (processedEventRepository.existsByEventId(eventId)) {
            log.info("skip: 이미 처리된 이벤트(중복). eventId={}", eventId);
            return;
        }

        if (result.status() != PayStatus.SUCCESS) {
            log.info("skip: 결제 성공 아님. eventId={}, paymentId={}, status={}", eventId, result.paymentId(), result.status());
            return;
        }

        // 주문 상태 변경
        Order order = orderRepository.findById(result.orderId())
                .orElseThrow(() -> new BusinessException(ErrorType.ORDER_NOT_FOUND, "orderId=" + result.orderId()));
        order.markAsPaid();

        // 성공 후처리
        afterSuccess(order);

        // 처리 완료 기록
        processedEventRepository.save(new ProcessedEvent(eventId));
    }

    @Transactional
    public void applyFailure(PaymentCompletedEvent result, String eventId) {
        if (processedEventRepository.existsByEventId(eventId)) {
            log.info("skip: 이미 처리된 이벤트(중복). eventId={}", eventId);
            return;
        }

        if (result.status() == PayStatus.SUCCESS) {
            log.info("skip: 결제 실패 아님. eventId={}, paymentId={}, status={}", eventId, result.paymentId(), result.status());
            return;
        }

        // 주문 상태 변경
        Order order = orderRepository.findById(result.orderId())
                .orElseThrow(() -> new BusinessException(ErrorType.ORDER_NOT_FOUND, "orderId=" + result.orderId()));
        order.markAsFailed();

        // 실패 후처리
        afterFailure(order);

        // 처리 완료 기록 (unique 제약으로 동시 중복 방지)
        processedEventRepository.save(new ProcessedEvent(eventId));
    }

    private void afterSuccess(Order order) {
        cartRepository.deleteById(order.getUserId());
    }

    private void afterFailure(Order order) {
        order.getOrderItems().forEach(item ->
                productRepository.increaseStock(item.getProduct().getId(), item.getQuantity()));
    }
}

