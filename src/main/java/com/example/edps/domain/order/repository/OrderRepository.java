package com.example.edps.domain.order.repository;

import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.enums.OrderStatus;
import com.example.edps.domain.payment.enums.PayStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"payment"})
    Optional<Order> findWithPaymentById(Long id);

    @EntityGraph(attributePaths = {"payment"})
    Optional<Order> findTop1ByUserIdOrderByIdDesc(String userId);

    Optional<Order> findByPayment_Id(Long paymentId);

    @Query("""
        SELECT DISTINCT o
        FROM Order o
        JOIN FETCH o.orderItems oi
        JOIN FETCH oi.product p
        JOIN FETCH o.payment pay
        WHERE pay.status = :status
          AND pay.requestedAt < :cutoff
          AND pay.completedAt IS NULL
          AND o.status NOT IN (:final1, :final2)
    """)
    List<Order> findStuckOrdersWithItems(
            @Param("status") PayStatus status,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("final1") OrderStatus final1,
            @Param("final2") OrderStatus final2
    );
}

