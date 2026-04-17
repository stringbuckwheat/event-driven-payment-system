package com.example.edps.infra.kafka.dlq;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentDlqLogRepository extends JpaRepository<PaymentDlqLog, Long> {
}
