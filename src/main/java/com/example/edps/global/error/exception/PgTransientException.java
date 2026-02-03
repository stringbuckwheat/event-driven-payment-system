package com.example.edps.global.error.exception;

public class PgTransientException extends RuntimeException {
    public final int status;
    public final String body;
    public PgTransientException(int status, String body) {
        super("PG transient error status=" + status + " body=" + body);
        this.status = status;
        this.body = body;
    }
}
