package com.southwestasiafloat.backend.infrastructure.ratelimit;

import com.southwestasiafloat.backend.application.ratelimit.RateLimitDecision;
import com.southwestasiafloat.backend.application.ratelimit.TokenBucketRule;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryTokenBucketRateLimiterTest {

    @Test
    void consumesCapacityAndRefillsOverTime() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-08T00:00:00Z"));
        InMemoryTokenBucketRateLimiter limiter = new InMemoryTokenBucketRateLimiter(clock);
        TokenBucketRule rule = new TokenBucketRule(2, 2, Duration.ofSeconds(10), Duration.ofMinutes(5));

        assertTrue(limiter.tryConsume("bucket", rule).allowed());
        assertTrue(limiter.tryConsume("bucket", rule).allowed());

        RateLimitDecision blocked = limiter.tryConsume("bucket", rule);
        assertFalse(blocked.allowed());
        assertEquals(Duration.ofSeconds(5), blocked.retryAfter());

        clock.advance(Duration.ofSeconds(5));
        RateLimitDecision refilled = limiter.tryConsume("bucket", rule);
        assertTrue(refilled.allowed());
        assertEquals(0, refilled.remainingTokens());
    }

    @Test
    void refundReturnsConsumedToken() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-08T00:00:00Z"));
        InMemoryTokenBucketRateLimiter limiter = new InMemoryTokenBucketRateLimiter(clock);
        TokenBucketRule rule = new TokenBucketRule(1, 1, Duration.ofMinutes(1), Duration.ofMinutes(5));

        assertTrue(limiter.tryConsume("bucket", rule).allowed());
        assertFalse(limiter.tryConsume("bucket", rule).allowed());

        limiter.refund("bucket", rule);

        assertTrue(limiter.tryConsume("bucket", rule).allowed());
    }

    @Test
    void idleBucketExpiresBackToFullCapacity() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-08T00:00:00Z"));
        InMemoryTokenBucketRateLimiter limiter = new InMemoryTokenBucketRateLimiter(clock);
        TokenBucketRule rule = new TokenBucketRule(1, 1, Duration.ofHours(1), Duration.ofMinutes(1));

        assertTrue(limiter.tryConsume("bucket", rule).allowed());
        assertFalse(limiter.tryConsume("bucket", rule).allowed());

        clock.advance(Duration.ofMinutes(2));

        assertTrue(limiter.tryConsume("bucket", rule).allowed());
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
