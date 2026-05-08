package com.southwestasiafloat.backend.application.ratelimit;

import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitExceededException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter == null || retryAfter.isNegative()
                ? Duration.ZERO
                : retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
