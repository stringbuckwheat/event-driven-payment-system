package com.example.edps.global.lock;

public class LockAcquisitionException extends RuntimeException {
    public LockAcquisitionException(String lockKey) {
        super("레디스 락 획득 실패: " + lockKey);
    }
}