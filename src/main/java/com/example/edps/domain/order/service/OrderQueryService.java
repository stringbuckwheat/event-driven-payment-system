package com.example.edps.domain.order.service;

import com.example.edps.domain.order.dto.OrderStatusResponse;
import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.repository.OrderRepository;
import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.ElementNotFoundException;
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
                .orElseThrow(() -> new ElementNotFoundException(ErrorType.ORDER_NOT_FOUND, "orderId=" + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("not your order"); // TODO
        }

        return OrderStatusResponse.of(order, order.getPayment());
    }

    @Transactional(readOnly = true)
    public OrderStatusResponse getLatestOrderStatus(String userId) {
        Order order = orderRepository.findTop1ByUserIdOrderByIdDesc(userId)
                .orElseThrow(() -> new ElementNotFoundException(ErrorType.ORDER_NOT_FOUND, "userId=" + userId));

        return OrderStatusResponse.of(order, order.getPayment());
    }
}
