package com.southwestasiafloat.backend.application.ratelimit;

import java.time.Duration;

public record TokenBucketRule(
        int capacity,
        int refillTokens,
        Duration refillPeriod,
        Duration idleTtl
) {

    public TokenBucketRule {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Token bucket capacity must be positive.");
        }
        if (refillTokens <= 0) {
            throw new IllegalArgumentException("Token bucket refill tokens must be positive.");
        }
        if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("Token bucket refill period must be positive.");
        }
        if (idleTtl == null || idleTtl.isZero() || idleTtl.isNegative()) {
            throw new IllegalArgumentException("Token bucket idle TTL must be positive.");
        }
    }
}
