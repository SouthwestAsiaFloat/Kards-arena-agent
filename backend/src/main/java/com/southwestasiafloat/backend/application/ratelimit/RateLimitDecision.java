package com.southwestasiafloat.backend.application.ratelimit;

import java.time.Duration;

public record RateLimitDecision(
        boolean allowed,
        long remainingTokens,
        Duration retryAfter
) {

    public static RateLimitDecision allowed(long remainingTokens) {
        return new RateLimitDecision(true, Math.max(0, remainingTokens), Duration.ZERO);
    }

    public static RateLimitDecision blocked(long remainingTokens, Duration retryAfter) {
        Duration safeRetryAfter = retryAfter == null || retryAfter.isNegative()
                ? Duration.ZERO
                : retryAfter;
        return new RateLimitDecision(false, Math.max(0, remainingTokens), safeRetryAfter);
    }
}
