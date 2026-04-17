package com.example.edps.infra.kafka.dlq.repository;

import com.example.edps.infra.kafka.dlq.entity.PaymentDlqLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentDlqLogRepository extends JpaRepository<PaymentDlqLog, Long> {
}
