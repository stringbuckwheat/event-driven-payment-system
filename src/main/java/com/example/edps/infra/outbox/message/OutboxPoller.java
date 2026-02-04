package com.example.edps.infra.outbox.message;

import com.example.edps.infra.outbox.entity.OutboxEvent;
import com.example.edps.infra.outbox.enums.OutboxStatus;
import com.example.edps.infra.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {
    private final OutboxRepository outboxRepository;
    private final OutboxKafkaPublisher publisher;
    private final OutboxDispatcher dispatcher;
    private final int batchSize = 50;

    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:1000}")
    public void pollAndPublish() {
        List<OutboxEvent> candidates =
                outboxRepository.findBatchByStatus(OutboxStatus.PENDING, PageRequest.of(0, batchSize));

        if (candidates.isEmpty()) return;

        for (OutboxEvent e : candidates) {
            if (!dispatcher.claim(e.getId())) continue;

            try {
                OutboxEvent event = dispatcher.getForPublish(e.getId());
                publisher.publish(event);

                dispatcher.markSent(e.getId());

                log.info("outbox publish 성공 eventId={}, topic={}, key={}",
                        event.getEventId(), event.getTopic(), event.getMessageKey());

            } catch (Exception ex) {
                dispatcher.recordFailure(e.getId(), ex);

                log.warn("outbox publish 실패 outboxId={}, error={}", e.getId(), ex.getMessage(), ex);
            }
        }
    }
}
