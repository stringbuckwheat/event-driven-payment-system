package com.example.edps.domain.order.repository;

import com.example.edps.domain.order.entity.Payment;
import com.example.edps.domain.order.enums.PayStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :to WHERE p.id = :paymentId AND p.status = :from")
    int transitionStatus(@Param("paymentId") Long paymentId,
                         @Param("from") PayStatus from,
                         @Param("to") PayStatus to);
}
