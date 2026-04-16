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

    /**
     * 결제 성공 후처리
     * @param event
     * @param eventId
     */
    @Transactional
    public void applySuccess(PaymentCompletedEvent event, String eventId) {
        // processedEvent 조회
        if (isDuplicate(eventId)) return;

        // 주문 상태 결제 완료로 처리
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new BusinessException(ErrorType.ORDER_NOT_FOUND, "orderId=" + event.orderId()));
        order.markAsPaid();
        clearCart(order); // 카트 비우기

        processedEventRepository.save(new ProcessedEvent(eventId));
        log.info("결제 성공 처리 완료 orderId={}, paymentId={}", event.orderId(), event.paymentId());
    }

    @Transactional
    public void applyFailure(PaymentCompletedEvent event, String eventId) {
        if (isDuplicate(eventId)) return;

        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new BusinessException(ErrorType.ORDER_NOT_FOUND, "orderId=" + event.orderId()));
        order.markAsFailed();
        rollbackStock(order);

        processedEventRepository.save(new ProcessedEvent(eventId));
        log.info("결제 실패 처리 완료 orderId={}, paymentId={}", event.orderId(), event.paymentId());
    }

    private boolean isDuplicate(String eventId) {
        if (processedEventRepository.existsByEventId(eventId)) {
            log.info("skip: 이미 처리된 이벤트 eventId={}", eventId);
            return true;
        }
        return false;
    }

    /**
     * 결제 성공 후 장바구니 삭제
     */
    private void clearCart(Order order) {
        cartRepository.deleteById(order.getUserId());
    }

    /**
     * 결제 실패 후 재고 롤백
     */
    private void rollbackStock(Order order) {
        order.getOrderItems().forEach(item ->
                productRepository.increaseStock(item.getProduct().getId(), item.getQuantity())
        );
    }
}
