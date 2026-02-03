package com.example.edps.infra.outbox.message;

import com.example.edps.global.error.ErrorType;
import com.example.edps.global.error.exception.ElementNotFoundException;
import com.example.edps.infra.outbox.entity.OutboxEvent;
import com.example.edps.infra.outbox.enums.OutboxStatus;
import com.example.edps.infra.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxDispatcher {
    private final OutboxRepository outboxRepository;
    private final int maxAttempts = 10;

    @Transactional
    public boolean claim(Long id) {
        int updated = outboxRepository.transitionStatus(id, OutboxStatus.PENDING, OutboxStatus.PROCESSING);
        return updated == 1;
    }

    // publish 전에 필요한 정보 읽기
    @Transactional(readOnly = true)
    public OutboxEvent getForPublish(Long id) {
        return outboxRepository.findById(id)
                .orElseThrow(() -> new ElementNotFoundException(ErrorType.OUTBOX_NOT_FOUND, "outbox id=" + id));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long id) {
        OutboxEvent event = outboxRepository.findById(id)
                .orElseThrow(() -> new ElementNotFoundException(ErrorType.OUTBOX_NOT_FOUND, "outbox id=" + id));

        event.markSent();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long id, Exception ex) {
        OutboxEvent event = outboxRepository.findById(id)
                .orElseThrow(() -> new ElementNotFoundException(ErrorType.OUTBOX_NOT_FOUND, "outbox id=" + id));

        String raw = ex.getMessage();
        String message =
                (raw == null) ? "no-message"
                        : (raw.length() <= 500 ? raw : raw.substring(0, 500));

        event.recordFailure(message, maxAttempts);
    }
}
