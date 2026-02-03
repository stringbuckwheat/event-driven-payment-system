package com.example.edps.domain.order.controller;

import com.example.edps.domain.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/order")
public class OrderController {
    private final OrderService orderService;
    private static final String USER_ID_HEADER = "X-USER-ID";

    @PostMapping
    public ResponseEntity<Void> getCart(@RequestHeader(USER_ID_HEADER) String userId) {
        orderService.order(userId);
        return ResponseEntity.ok().build();
    }
}
