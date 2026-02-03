package com.example.edps.global.error.exception;

import lombok.Getter;

@Getter
public class PgBusinessException extends RuntimeException {
    private final int status;
    private final String body;

    public PgBusinessException(int status, String body) {
        super("PG business error status=" + status + " body=" + body);
        this.status = status;
        this.body = body;
    }
}