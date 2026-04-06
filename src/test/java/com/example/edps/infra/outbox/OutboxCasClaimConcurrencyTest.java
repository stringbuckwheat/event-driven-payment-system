//package com.example.edps.infra.outbox;
//
//import com.example.edps.infra.outbox.entity.OutboxEvent;
//import com.example.edps.infra.outbox.enums.OutboxStatus;
//import com.example.edps.infra.outbox.message.OutboxDispatcher;
//import com.example.edps.infra.outbox.repository.OutboxRepository;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
//import org.springframework.context.annotation.Import;
//import org.springframework.test.context.TestPropertySource;
//import org.springframework.transaction.PlatformTransactionManager;
//import org.springframework.transaction.annotation.Propagation;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.transaction.support.TransactionTemplate;
//
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.concurrent.atomic.AtomicReference;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
///**
// * Outbox Poller CAS(Compare-And-Swap) claim 동시성 테스트
// * 여러 인스턴스가 동시에 폴링하더라도 동일 이벤트가 중복 발행되지 않도록 선점
// *
// * 10개 인스턴스(스레드)가 동시에 동일한 OutboxEvent에 claim을 시도할 때
// * 정확히 1개만 성공해야 → Kafka publish가 1번만 발생함을 보장
// */
//@DataJpaTest
//@Import(OutboxDispatcher.class)
//@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=20")
//@Transactional(propagation = Propagation.NOT_SUPPORTED)
//class OutboxCasClaimConcurrencyTest {
//
//    @Autowired OutboxDispatcher outboxDispatcher;
//    @Autowired OutboxRepository outboxRepository;
//    @Autowired PlatformTransactionManager txManager;
//
//    private Long outboxId;
//    private final AtomicReference<Throwable> threadError = new AtomicReference<>();
//
//    @BeforeEach
//    void setUp() {
//        threadError.set(null);
//        outboxId = new TransactionTemplate(txManager).execute(status -> {
//            OutboxEvent event = OutboxEvent.pending(
//                    "evt-concurrency-test", "trace-concurrency",
//                    "payment.command.requested", "payment.command.requested",
//                    "key-1", "{\"paymentId\":1}"
//            );
//            outboxRepository.save(event);
//            System.out.println("[준비] OutboxEvent 생성 완료 - id: " + event.getId() + ", 상태: PENDING");
//            return event.getId();
//        });
//    }
//
//    @AfterEach
//    void tearDown() {
//        new TransactionTemplate(txManager).execute(status -> {
//            outboxRepository.deleteAll();
//            System.out.println("[정리] 테스트 데이터 삭제 완료");
//            return null;
//        });
//    }
//
//    @Test
//    @DisplayName("10개 인스턴스가 동시에 claim을 시도해도 단 하나만 PROCESSING 상태로 선점된다")
//    void only_one_instance_can_claim_outbox_event_among_concurrent_attempts() throws InterruptedException {
//        int threadCount = 10;
//        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
//        CountDownLatch ready = new CountDownLatch(threadCount);
//        CountDownLatch start = new CountDownLatch(1);
//        CountDownLatch done  = new CountDownLatch(threadCount);
//        AtomicInteger successCount = new AtomicInteger(0);
//        AtomicInteger failCount    = new AtomicInteger(0);
//
//        System.out.println("[테스트] 동시 인스턴스 " + threadCount + "개 시작 - outboxId: " + outboxId);
//
//        for (int i = 0; i < threadCount; i++) {
//            final int instanceId = i + 1;
//            executor.submit(() -> {
//                ready.countDown();
//                try {
//                    start.await();
//                    System.out.printf("[인스턴스-%02d] CAS claim 시도 중...%n", instanceId);
//
//                    boolean claimed = outboxDispatcher.claim(outboxId);
//                    if (claimed) {
//                        successCount.incrementAndGet();
//                        System.out.printf("[인스턴스-%02d] ✅ 선점 성공 - PROCESSING 상태 획득%n", instanceId);
//                    } else {
//                        failCount.incrementAndGet();
//                        System.out.printf("[인스턴스-%02d] ❌ 선점 실패 - 이미 다른 인스턴스가 선점%n", instanceId);
//                    }
//                } catch (Exception e) {
//                    threadError.compareAndSet(null, e);
//                    System.out.printf("[인스턴스-%02d] 💥 예외 발생 - %s%n", instanceId, e.getMessage());
//                } finally {
//                    done.countDown();
//                }
//            });
//        }
//
//        ready.await();
//        System.out.println("[테스트] 전체 인스턴스 준비 완료 — 동시 출발 신호 발사");
//        start.countDown();
//        done.await(10, TimeUnit.SECONDS);
//        executor.shutdown();
//
//        System.out.println("─".repeat(50));
//        System.out.println("[결과] 성공: " + successCount.get() + "개 / 실패: " + failCount.get() + "개");
//        System.out.println("─".repeat(50));
//
//        assertThat(threadError.get())
//                .as("스레드 내에서 예외가 발생하지 않아야 한다")
//                .isNull();
//
//        assertThat(successCount.get())
//                .as("CAS claim은 %d개의 동시 요청 중 정확히 1개만 성공해야 한다", threadCount)
//                .isEqualTo(1);
//
//        new TransactionTemplate(txManager).execute(s -> {
//            OutboxEvent result = outboxRepository.findById(outboxId).orElseThrow();
//            System.out.println("[검증] 최종 OutboxEvent 상태: " + result.getStatus());
//            assertThat(result.getStatus())
//                    .as("선점에 성공한 단 하나의 인스턴스만 PROCESSING 상태여야 한다")
//                    .isEqualTo(OutboxStatus.PROCESSING);
//            return null;
//        });
//    }
//}