package com.example.edps.global.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 분산락 어노테이션
 *
 * key: 락 키 지정 (예: "'payment-claim-' + #paymentId")
 * leaseTime: 락 자동 해제 시간 (default 30초)
 * throwOnFail: 락 획득 실패 시 LockAcquisitionException 발생 여부 (default false)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    String key();
    long leaseTime() default 30;
    TimeUnit timeUnit() default TimeUnit.SECONDS;
    boolean throwOnFail() default false;
}