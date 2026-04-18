package com.example.edps.infra.processedevent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProcessedEvent UNIQUE 제약 동시성 테스트
 *
 * 동일 eventId가 동시에 INSERT해도 UNIQUE 제약(uk_processed_event_id)으로
 * 단 하나만 커밋되고 나머지는 DataIntegrityViolationException 이 발생해야함
 *
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {"spring.datasource.hikari.maximum-pool-size=20"})
class ProcessedEventConcurrencyTest {

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    private final AtomicReference<Throwable> threadError = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        new TransactionTemplate(txManager).execute(status -> {
            processedEventRepository.deleteAll();
            System.out.println("[정리] 테스트 데이터 삭제 완료");
            return null;
        });
        threadError.set(null);
    }

    /**
     * 시나리오 1: 동시 INSERT — UNIQUE 제약으로 중복 차단
     * 스레드 2개가 동일 eventId로 동시에 save()를 시도하면
     * 1개만 커밋되고 나머지 1개는 DataIntegrityViolationException
     */
    @Test
    @DisplayName("동일 eventId로 동시 INSERT 시 UNIQUE 제약으로 하나만 커밋되고 나머지는 DataIntegrityViolationException 발생")
    void concurrent_insert_same_eventId_only_one_succeeds() throws InterruptedException {
        String eventId = "dup-event-xyz";
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger dataIntegrityViolationCount = new AtomicInteger(0);

        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        txTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        System.out.println("[시나리오 1] 동일 eventId 동시 INSERT — eventId: " + eventId);

        for (int i = 0; i < threadCount; i++) {
            final int workerId = i + 1;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    System.out.printf("[스레드-%d] INSERT 시도 중...%n", workerId);

                    txTemplate.execute(status -> {
                        processedEventRepository.save(new ProcessedEvent(eventId));
                        return null;
                    });

                    successCount.incrementAndGet();
                    System.out.printf("[스레드-%d] ✅ INSERT 성공%n", workerId);
                } catch (DataIntegrityViolationException e) {
                    dataIntegrityViolationCount.incrementAndGet();
                    System.out.printf("[스레드-%d] ❌ DataIntegrityViolationException (정상) — UNIQUE 제약 동작%n", workerId);
                } catch (Exception e) {
                    threadError.compareAndSet(null, e);
                    System.out.printf("[스레드-%d] 💥 예상치 못한 예외 — %s%n", workerId, e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        System.out.println("[시나리오 1] 스레드 준비 완료 — 동시 출발 신호");
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("-----------------------------------------------------------------");
        System.out.printf("[RESULT] success: %d / dataIntegrityViolation: %d%n",
                successCount.get(), dataIntegrityViolationCount.get());
        System.out.println("-----------------------------------------------------------------");

        assertThat(threadError.get())
                .as("DataIntegrityViolationException 외 예외가 발생하지 않아야 한다")
                .isNull();
        assertThat(successCount.get())
                .as("동일 eventId INSERT 중 정확히 1개만 커밋되어야 한다")
                .isEqualTo(1);
        assertThat(dataIntegrityViolationCount.get())
                .as("나머지 1개는 UNIQUE 제약으로 DataIntegrityViolationException이 발생해야 한다")
                .isEqualTo(1);

        new TransactionTemplate(txManager).execute(status -> {
            long count = processedEventRepository.findAll().stream()
                    .filter(e -> eventId.equals(e.getEventId()))
                    .count();
            System.out.println("[검증] DB 저장 레코드 수 (eventId=" + eventId + "): " + count);
            assertThat(count)
                    .as("DB에 해당 eventId 레코드는 정확히 1개여야 한다")
                    .isEqualTo(1);
            return null;
        });
    }

    // 시나리오 2: existsByEventId 체크 후 동시 INSERT — UNIQUE 제약이 최후 방어선
    @Test
    @DisplayName("existsByEventId 체크 후 동시 INSERT 시 UNIQUE 제약이 최후 방어선으로 동작한다")
    void unique_constraint_is_last_defense_when_check_then_insert_race() throws InterruptedException {
        String eventId = "dup-event-race";
        int threadCount = 2;
        CyclicBarrier checkBarrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger dataIntegrityViolationCount = new AtomicInteger(0);

        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        txTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        System.out.println("[시나리오 2] existsByEventId + INSERT 경쟁 — eventId: " + eventId);

        for (int i = 0; i < threadCount; i++) {
            final int workerId = i + 1;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    System.out.printf("[스레드-%d] existsByEventId 확인 후 INSERT 시도 중...%n", workerId);

                    txTemplate.execute(status -> {
                        // 실제 PaymentResultConsumer의 패턴: existsByEventId() 체크 후 처리
                        // READ_COMMITTED 격리 수준에서 두 스레드 모두 false를 읽음 (상대방 미커밋)
                        boolean exists = processedEventRepository.existsByEventId(eventId);
                        System.out.printf("[스레드-%d] existsByEventId 결과: %s%n", workerId, exists);

                        if (!exists) {
                            try {
                                // 두 스레드 모두 체크를 완료한 후 동시에 save() 진입을 보장
                                checkBarrier.await(5, TimeUnit.SECONDS);
                            } catch (Exception barrierEx) {
                                status.setRollbackOnly();
                                throw new RuntimeException("barrier 대기 실패", barrierEx);
                            }
                            processedEventRepository.save(new ProcessedEvent(eventId));
                        }
                        return null;
                    });

                    successCount.incrementAndGet();
                    System.out.printf("[스레드-%d] ✅ 처리 성공%n", workerId);
                } catch (DataIntegrityViolationException e) {
                    dataIntegrityViolationCount.incrementAndGet();
                    System.out.printf("[스레드-%d] ❌ DataIntegrityViolationException (정상) — UNIQUE 제약이 최후 방어선 역할%n", workerId);
                } catch (Exception e) {
                    threadError.compareAndSet(null, e);
                    System.out.printf("[스레드-%d] 💥 예상치 못한 예외 — %s%n", workerId, e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        System.out.println("[시나리오 2] 스레드 준비 완료 — 동시 출발 신호");
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("-----------------------------------------------------------------");
        System.out.printf("[RESULT] success: %d / dataIntegrityViolation: %d%n",
                successCount.get(), dataIntegrityViolationCount.get());
        System.out.println("-----------------------------------------------------------------");

        assertThat(threadError.get())
                .as("DataIntegrityViolationException 외 예외가 발생하지 않아야 한다")
                .isNull();
        assertThat(successCount.get())
                .as("existsByEventId 체크를 통과했어도 UNIQUE 제약으로 정확히 1개만 커밋되어야 한다")
                .isEqualTo(1);
        assertThat(dataIntegrityViolationCount.get())
                .as("UNIQUE 제약이 최후 방어선으로 동작해 나머지 1개를 차단해야 한다")
                .isEqualTo(1);

        new TransactionTemplate(txManager).execute(status -> {
            long count = processedEventRepository.findAll().stream()
                    .filter(e -> eventId.equals(e.getEventId()))
                    .count();
            System.out.println("[검증] DB 저장 레코드 수 (eventId=" + eventId + "): " + count);
            assertThat(count)
                    .as("UNIQUE 제약으로 동일 eventId 레코드는 정확히 1개여야 한다")
                    .isEqualTo(1);
            return null;
        });
    }
}
