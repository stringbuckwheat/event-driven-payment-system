package com.example.edps.domain.payment.repository;

import com.example.edps.domain.payment.entity.PaymentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentLogRepository extends JpaRepository<PaymentLog, Long> {
    @Query("SELECT COUNT(l) FROM PaymentLog l WHERE l.payment.id = :paymentId")
    long countByPaymentId(@Param("paymentId") Long paymentId);
}
