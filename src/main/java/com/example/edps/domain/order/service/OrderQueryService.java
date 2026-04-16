package com.example.edps.domain.order.service;

import com.example.edps.domain.order.dto.OrderStatusResponse;
import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.repository.OrderRepository;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public OrderStatusResponse getOrderStatus(Long orderId, String userId) {
        Order order = orderRepository.findWithPaymentById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorType.ORDER_NOT_FOUND, "orderId=" + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorType.ORDER_ACCESS_DENIED);
        }

        return OrderStatusResponse.of(order, order.getPayment());
    }

    @Transactional(readOnly = true)
    public OrderStatusResponse getLatestOrderStatus(String userId) {
        Order order = orderRepository.findTop1ByUserIdOrderByIdDesc(userId)
                .orElseThrow(() -> new BusinessException(ErrorType.ORDER_NOT_FOUND, "userId=" + userId));

        return OrderStatusResponse.of(order, order.getPayment());
    }
}
