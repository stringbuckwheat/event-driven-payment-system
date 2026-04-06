package com.example.edps.domain.payment.service;

import com.example.edps.domain.order.entity.Order;
import com.example.edps.domain.order.repository.OrderRepository;
import com.example.edps.domain.payment.entity.Payment;
import com.example.edps.domain.payment.enums.PayStatus;
import com.example.edps.domain.payment.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 워커 claim 동시성 테스트
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {"spring.datasource.hikari.maximum-pool-size=20"})
class PaymentCasClaimConcurrencyTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    private Long paymentId;

    private final AtomicReference<Throwable> threadError = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        threadError.set(null);
        paymentId = new TransactionTemplate(txManager).execute(status -> {
            Order order = Order.create("concurrent-test-user", List.of(), 10_000);
            Payment payment = new Payment(order);
            order.attachPayment(payment);
            orderRepository.save(order);

            Long pid = order.getPayment().getId();
            System.out.println("[setUp()] Payment 생성 완료 - id: " + pid + ", 상태: REQUESTED");
            return pid;
        });
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(txManager).execute(status -> {
            paymentRepository.deleteAll();
            orderRepository.deleteAll();
            System.out.println("[정리] 테스트 데이터 삭제 완료");
            return null;
        });
    }

    @Test
    @DisplayName("10개의 워커가 동시에 claim을 시도해도 단 하나만 PROCESSING 상태로 선점된다")
    void only_one_worker_can_claim_payment_among_concurrent_attempts() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        txTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        System.out.println("[테스트] 동시 워커 " + threadCount + "개 시작 - paymentId: " + paymentId);

        for (int i = 0; i < threadCount; i++) {
            final int workerId = i + 1;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    System.out.printf("[워커-%02d] CAS claim 시도 중...%n", workerId);

                    Boolean claimed = txTemplate.execute(s ->
                            paymentRepository.transitionStatus(
                                    paymentId, PayStatus.READY, PayStatus.PROCESSING) == 1
                    );

                    if (Boolean.TRUE.equals(claimed)) {
                        successCount.incrementAndGet();
                        System.out.printf("[워커-%02d] ✅ 선점 성공 - PROCESSING 상태 획득%n", workerId);
                    } else {
                        failCount.incrementAndGet();
                        System.out.printf("[워커-%02d] ❌ 선점 실패 - 이미 다른 워커가 선점%n", workerId);
                    }
                } catch (Exception e) {
                    threadError.compareAndSet(null, e);
                    System.out.printf("[워커-%02d] 💥 예외 발생 - %s%n", workerId, e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        System.out.println("[테스트] 전체 워커 준비 완료 — 동시 출발 신호");
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("-----------------------------------------------------------------");
        System.out.println("[RESULT] success: " + successCount.get() + " / fail: " + failCount.get());
        System.out.println("-----------------------------------------------------------------");

        assertThat(threadError.get())
                .as("스레드 내에서 예외가 발생하지 않아야 한다")
                .isNull();

        assertThat(successCount.get())
                .as("CAS claim은 %d개의 동시 요청 중 정확히 1개만 성공해야 한다", threadCount)
                .isEqualTo(1);

        new TransactionTemplate(txManager).execute(s -> {
            Payment result = paymentRepository.findById(paymentId).orElseThrow();
            System.out.println("[검증] 최종 Payment 상태: " + result.getStatus());
            assertThat(result.getStatus())
                    .as("선점에 성공한 단 하나의 워커만 PROCESSING 상태여야 한다")
                    .isEqualTo(PayStatus.PROCESSING);
            return null;
        });
    }
}