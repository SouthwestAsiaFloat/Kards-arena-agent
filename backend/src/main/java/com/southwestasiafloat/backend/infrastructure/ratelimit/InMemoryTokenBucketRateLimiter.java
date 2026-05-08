package com.southwestasiafloat.backend.infrastructure.ratelimit;

import com.southwestasiafloat.backend.application.ratelimit.RateLimitDecision;
import com.southwestasiafloat.backend.application.ratelimit.TokenBucketRateLimiter;
import com.southwestasiafloat.backend.application.ratelimit.TokenBucketRule;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ConditionalOnMissingBean(RedissonClient.class)
public class InMemoryTokenBucketRateLimiter implements TokenBucketRateLimiter {

    private final ConcurrentMap<String, BucketState> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryTokenBucketRateLimiter() {
        this(Clock.systemUTC());
    }

    InMemoryTokenBucketRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public RateLimitDecision tryConsume(String bucketKey, TokenBucketRule rule) {
        long nowMs = clock.millis();
        BucketState state = getOrCreateState(bucketKey, rule, nowMs);
        synchronized (state) {
            refill(state, rule, nowMs);
            state.lastSeenAtMs = nowMs;
            if (state.tokens >= 1.0d) {
                state.tokens -= 1.0d;
                return RateLimitDecision.allowed((long) Math.floor(state.tokens));
            }

            double missingTokens = 1.0d - state.tokens;
            Duration retryAfter = retryAfter(missingTokens, rule);
            return RateLimitDecision.blocked(0, retryAfter);
        }
    }

    @Override
    public void refund(String bucketKey, TokenBucketRule rule) {
        long nowMs = clock.millis();
        BucketState state = getOrCreateState(bucketKey, rule, nowMs);
        synchronized (state) {
            refill(state, rule, nowMs);
            state.tokens = Math.min(rule.capacity(), state.tokens + 1.0d);
            state.lastSeenAtMs = nowMs;
        }
    }

    private BucketState getOrCreateState(String bucketKey, TokenBucketRule rule, long nowMs) {
        return buckets.compute(bucketKey, (key, current) -> {
            if (current == null || isExpired(current, rule, nowMs)) {
                return new BucketState(rule.capacity(), nowMs);
            }
            return current;
        });
    }

    private boolean isExpired(BucketState state, TokenBucketRule rule, long nowMs) {
        return nowMs - state.lastSeenAtMs > Math.max(1, rule.idleTtl().toMillis());
    }

    private void refill(BucketState state, TokenBucketRule rule, long nowMs) {
        long elapsedMs = Math.max(0, nowMs - state.refreshedAtMs);
        if (elapsedMs <= 0 || state.tokens >= rule.capacity()) {
            state.refreshedAtMs = nowMs;
            return;
        }

        double refillRatePerMs = (double) rule.refillTokens() / Math.max(1, rule.refillPeriod().toMillis());
        state.tokens = Math.min(rule.capacity(), state.tokens + elapsedMs * refillRatePerMs);
        state.refreshedAtMs = nowMs;
    }

    private Duration retryAfter(double missingTokens, TokenBucketRule rule) {
        double refillRatePerMs = (double) rule.refillTokens() / Math.max(1, rule.refillPeriod().toMillis());
        long retryAfterMs = (long) Math.ceil(missingTokens / refillRatePerMs);
        return Duration.ofMillis(Math.max(1, retryAfterMs));
    }

    private static class BucketState {
        private double tokens;
        private long refreshedAtMs;
        private long lastSeenAtMs;

        private BucketState(double tokens, long nowMs) {
            this.tokens = tokens;
            this.refreshedAtMs = nowMs;
            this.lastSeenAtMs = nowMs;
        }
    }
}
