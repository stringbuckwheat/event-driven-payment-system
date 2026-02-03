package com.example.edps.infra.outbox.repository;

import com.example.edps.infra.outbox.entity.OutboxEvent;
import com.example.edps.infra.outbox.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    @Query("""
        SELECT e
        FROM OutboxEvent e
        WHERE e.status = :status
        ORDER BY e.createdAt ASC
    """)
    List<OutboxEvent> findBatchByStatus(@Param("status") OutboxStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE OutboxEvent e
        SET e.status = :to
        WHERE e.id = :id AND e.status = :from
    """)
    int transitionStatus(
            @Param("id") Long id,
            @Param("from") OutboxStatus from,
            @Param("to") OutboxStatus to
    );
}
