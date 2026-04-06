package com.example.edps.infra.outbox.repository;

import com.example.edps.infra.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
}
