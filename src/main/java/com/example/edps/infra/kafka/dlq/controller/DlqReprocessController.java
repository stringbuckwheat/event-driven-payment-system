package com.example.edps.infra.kafka.dlq.controller;

import com.example.edps.infra.kafka.dlq.service.DlqReprocessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dlq")
@RequiredArgsConstructor
public class DlqReprocessController {

    private final DlqReprocessService dlqReprocessService;

    /**
     * DLQ 실패 건 재처리
     *
     * @param dlqLogId PaymentDlqLog PK
     */
    @PostMapping("/{dlqLogId}/reprocess")
    public ResponseEntity<Void> reprocess(@PathVariable Long dlqLogId) {
        dlqReprocessService.reprocess(dlqLogId);
        return ResponseEntity.ok().build();
    }
}
