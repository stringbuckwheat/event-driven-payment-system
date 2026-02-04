package com.example.edps.domain.order.repository;

import com.example.edps.domain.order.entity.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"payment"})
    Optional<Order> findWithPaymentById(Long id);

    @EntityGraph(attributePaths = {"payment"})
    Optional<Order> findTop1ByUserIdOrderByIdDesc(String userId);
}

