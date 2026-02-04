package com.example.edps.domain.order.controller;

import com.example.edps.domain.order.dto.OrderStatusResponse;
import com.example.edps.domain.order.service.OrderQueryService;
import com.example.edps.domain.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orderService;
    private final OrderQueryService orderQueryService;
    private static final String USER_ID_HEADER = "X-USER-ID";

    @PostMapping
    public ResponseEntity<Void> getCart(@RequestHeader(USER_ID_HEADER) String userId) {
        orderService.order(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/status")
    public OrderStatusResponse getStatus(@PathVariable Long id,
                                         @RequestHeader("X-USER-ID") String userId) {
        return orderQueryService.getOrderStatus(id, userId);
    }

    @GetMapping("/latest/status")
    public OrderStatusResponse getLatestStatus(@RequestHeader("X-USER-ID") String userId) {
        return orderQueryService.getLatestOrderStatus(userId);
    }
}
