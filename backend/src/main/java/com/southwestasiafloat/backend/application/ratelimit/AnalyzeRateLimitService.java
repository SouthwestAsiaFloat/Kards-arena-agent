package com.southwestasiafloat.backend.application.ratelimit;

import com.southwestasiafloat.backend.config.ArenaRateLimitProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnalyzeRateLimitService {

    private final ArenaRateLimitProperties properties;
    private final TokenBucketRateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;

    public AnalyzeRateLimitService(ArenaRateLimitProperties properties,
                                   TokenBucketRateLimiter rateLimiter,
                                   MeterRegistry meterRegistry) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
    }

    public void checkAnalyzeAllowed(String sessionId, String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }

        List<ConsumedBucket> consumedBuckets = new ArrayList<>();
        try {
            checkSessionBucket(sessionId, consumedBuckets);
            checkIpBucket(clientIp, consumedBuckets);
        } catch (RateLimitExceededException ex) {
            for (ConsumedBucket consumedBucket : consumedBuckets) {
                rateLimiter.refund(consumedBucket.bucketKey(), consumedBucket.rule());
            }
            throw ex;
        }
    }

    private void checkSessionBucket(String sessionId, List<ConsumedBucket> consumedBuckets) {
        boolean ruleEnabled = isRuleEnabled(
                properties.getPerSessionCapacity(),
                properties.getPerSessionRefillTokens(),
                properties.getPerSessionRefillPeriod()
        );
        if (!properties.isPerSessionEnabled() || !hasText(sessionId) || !ruleEnabled) {
            return;
        }
        TokenBucketRule rule = new TokenBucketRule(
                properties.getPerSessionCapacity(),
                properties.getPerSessionRefillTokens(),
                properties.getPerSessionRefillPeriod(),
                properties.getBucketTtl()
        );
        String bucketKey = properties.getKeyPrefix() + ":session:" + safeKeyPart(sessionId);
        consume(bucketKey, rule, "session", consumedBuckets);
    }

    private void checkIpBucket(String clientIp, List<ConsumedBucket> consumedBuckets) {
        boolean ruleEnabled = isRuleEnabled(
                properties.getPerIpCapacity(),
                properties.getPerIpRefillTokens(),
                properties.getPerIpRefillPeriod()
        );
        if (!properties.isPerIpEnabled() || !hasText(clientIp) || !ruleEnabled) {
            return;
        }
        TokenBucketRule rule = new TokenBucketRule(
                properties.getPerIpCapacity(),
                properties.getPerIpRefillTokens(),
                properties.getPerIpRefillPeriod(),
                properties.getBucketTtl()
        );
        String bucketKey = properties.getKeyPrefix() + ":ip:" + safeKeyPart(clientIp);
        consume(bucketKey, rule, "ip", consumedBuckets);
    }

    private void consume(String bucketKey,
                         TokenBucketRule rule,
                         String scope,
                         List<ConsumedBucket> consumedBuckets) {
        RateLimitDecision decision = rateLimiter.tryConsume(bucketKey, rule);
        if (decision.allowed()) {
            consumedBuckets.add(new ConsumedBucket(bucketKey, rule));
            meterRegistry.counter("arena.rate_limit.analyze.requests", "scope", scope, "result", "allowed").increment();
            return;
        }

        meterRegistry.counter("arena.rate_limit.analyze.requests", "scope", scope, "result", "blocked").increment();
        throw new RateLimitExceededException(
                "Analyze rate limit exceeded for " + scope + ". Please retry later.",
                decision.retryAfter()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isRuleEnabled(int capacity, int refillTokens, Duration refillPeriod) {
        return capacity > 0
                && refillTokens > 0
                && refillPeriod != null
                && !refillPeriod.isZero()
                && !refillPeriod.isNegative();
    }

    private String safeKeyPart(String value) {
        String trimmed = value.trim();
        StringBuilder builder = new StringBuilder(Math.min(trimmed.length(), 128));
        for (int i = 0; i < trimmed.length() && builder.length() < 128; i++) {
            char ch = trimmed.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '.' || ch == '_' || ch == '-' || ch == ':') {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }
        return builder.length() == 0 ? "unknown" : builder.toString();
    }

    private record ConsumedBucket(String bucketKey, TokenBucketRule rule) {
    }
}
