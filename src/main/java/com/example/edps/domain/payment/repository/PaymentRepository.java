package com.example.edps.domain.payment.repository;

import com.example.edps.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;


public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
