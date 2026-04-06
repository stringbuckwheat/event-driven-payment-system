//package com.example.edps.infra.outbox;
//
//import com.example.edps.infra.outbox.entity.OutboxEvent;
//import com.example.edps.infra.outbox.enums.OutboxStatus;
//import com.example.edps.infra.outbox.message.OutboxDispatcher;
//import com.example.edps.infra.outbox.message.OutboxKafkaPublisher;
//import com.example.edps.infra.outbox.message.OutboxPoller;
//import com.example.edps.infra.outbox.repository.OutboxRepository;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.util.List;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.BDDMockito.then;
//import static org.mockito.Mockito.*;
//
///**
// * OutboxPoller 단위 테스트
// * 여러 인스턴스가 동시에 폴링하더라도 동일 이벤트가 중복 발행되지 않도록 선점
// */
//@ExtendWith(MockitoExtension.class)
//class OutboxPollerTest {
//
//    @Mock
//    private OutboxRepository outboxRepository;
//
//    @Mock
//    private OutboxKafkaPublisher publisher;
//
//    @Mock
//    private OutboxDispatcher dispatcher;
//
//    @InjectMocks
//    private OutboxPoller poller;
//
//    @Test
//    @DisplayName("PENDING 이벤트가 없으면 아무것도 처리하지 않는다")
//    void no_pending_events_skips_all_processing() {
//        // given
//        given(outboxRepository.findBatchByStatus(eq(OutboxStatus.PENDING), any())).willReturn(List.of());
//
//        // when
//        poller.pollAndPublish();
//
//        // then
//        then(dispatcher).shouldHaveNoInteractions();
//        then(publisher).shouldHaveNoInteractions();
//    }
//
//    @Test
//    @DisplayName("다른 인스턴스가 이미 선점한 이벤트는 publish하지 않는다")
//    void already_claimed_event_skips_publish() {
//        // given
//        OutboxEvent event = makeEvent(1L);
//        given(outboxRepository.findBatchByStatus(eq(OutboxStatus.PENDING), any()))
//                .willReturn(List.of(event));
//        given(dispatcher.claim(1L)).willReturn(false);
//
//        // when
//        poller.pollAndPublish();
//
//        // then
//        then(publisher).shouldHaveNoInteractions();
//        then(dispatcher).should(never()).markSent(any());
//        then(dispatcher).should(never()).recordFailure(any(), any());
//    }
//
//    @Test
//    @DisplayName("선점 성공 후 publish가 성공하면 markSent를 호출한다")
//    void successful_publish_after_claim_calls_mark_sent() {
//        // given
//        OutboxEvent event = makeEvent(1L);
//        given(outboxRepository.findBatchByStatus(eq(OutboxStatus.PENDING), any()))
//                .willReturn(List.of(event));
//        given(dispatcher.claim(1L)).willReturn(true);
//        given(dispatcher.getForPublish(1L)).willReturn(event);
//
//        // when
//        poller.pollAndPublish();
//
//        // then
//        then(publisher).should().publish(event);
//        then(dispatcher).should().markSent(1L);
//        then(dispatcher).should(never()).recordFailure(any(), any());
//    }
//
//    @Test
//    @DisplayName("publish 실패 시 recordFailure를 호출하고 markSent는 호출하지 않는다")
//    void failed_publish_calls_record_failure_and_skips_mark_sent() {
//        // given
//        OutboxEvent event = makeEvent(1L);
//        given(outboxRepository.findBatchByStatus(eq(OutboxStatus.PENDING), any())).willReturn(List.of(event));
//        given(dispatcher.claim(1L)).willReturn(true);
//        given(dispatcher.getForPublish(1L)).willReturn(event);
//        doThrow(new RuntimeException("Kafka unavailable")).when(publisher).publish(event);
//
//        // when
//        poller.pollAndPublish();
//
//        // then
//        then(dispatcher).should().recordFailure(eq(1L), any());
//        then(dispatcher).should(never()).markSent(any());
//    }
//
//    @Test
//    @DisplayName("여러 이벤트 중 일부가 실패해도 나머지 이벤트는 계속 처리된다")
//    void partial_failure_does_not_block_remaining_events() {
//        // given
//        OutboxEvent skipped = makeEvent(1L);
//        OutboxEvent failed = makeEvent(2L);
//        OutboxEvent success = makeEvent(3L);
//
//        given(outboxRepository.findBatchByStatus(eq(OutboxStatus.PENDING), any()))
//                .willReturn(List.of(skipped, failed, success));
//        given(dispatcher.claim(1L)).willReturn(false);
//        given(dispatcher.claim(2L)).willReturn(true);
//        given(dispatcher.claim(3L)).willReturn(true);
//        given(dispatcher.getForPublish(2L)).willReturn(failed);
//        given(dispatcher.getForPublish(3L)).willReturn(success);
//        doThrow(new RuntimeException("timeout")).when(publisher).publish(failed);
//
//        // when
//        poller.pollAndPublish();
//
//        // then
//        then(publisher).should(never()).publish(skipped);
//        then(dispatcher).should().recordFailure(eq(2L), any());
//        then(dispatcher).should().markSent(3L);
//        then(dispatcher).should(never()).markSent(1L);
//        then(dispatcher).should(never()).markSent(2L);
//    }
//
//    private OutboxEvent makeEvent(Long id) {
//        OutboxEvent event = OutboxEvent.pending(
//                "event-" + id, "trace-" + id,
//                "payment.command.requested", "payment.command.requested",
//                String.valueOf(id), "{}"
//        );
//        try {
//            var field = OutboxEvent.class.getDeclaredField("id");
//            field.setAccessible(true);
//            field.set(event, id);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//        return event;
//    }
//}