package com.example.edps.domain.order.controller;

import com.example.edps.domain.order.dto.OrderStatusResponse;
import com.example.edps.domain.order.enums.PgScenario;
import com.example.edps.domain.order.service.OrderQueryService;
import com.example.edps.domain.order.service.OrderService;
import com.example.edps.global.common.AppHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orderService;
    private final OrderQueryService orderQueryService;
    private static final String PG_SCENARIO = "PG-SCENARIO";

    @PostMapping
    public ResponseEntity<Void> order(@RequestHeader(AppHeaders.USER_ID) String userId,
                                      @RequestHeader(value = PG_SCENARIO, required = false) PgScenario scenario) {
        orderService.order(userId, scenario);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/status")
    public OrderStatusResponse getStatus(@PathVariable Long id,
                                         @RequestHeader(AppHeaders.USER_ID) String userId) {
        return orderQueryService.getOrderStatus(id, userId);
    }

    @GetMapping("/latest/status")
    public OrderStatusResponse getLatestStatus(@RequestHeader(AppHeaders.USER_ID) String userId) {
        return orderQueryService.getLatestOrderStatus(userId);
    }
}
