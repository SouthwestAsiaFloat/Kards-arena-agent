package com.southwestasiafloat.backend.application.ratelimit;

public interface TokenBucketRateLimiter {

    RateLimitDecision tryConsume(String bucketKey, TokenBucketRule rule);

    void refund(String bucketKey, TokenBucketRule rule);
}
