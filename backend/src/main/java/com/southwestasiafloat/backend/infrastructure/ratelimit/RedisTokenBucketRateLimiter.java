package com.southwestasiafloat.backend.infrastructure.ratelimit;

import com.southwestasiafloat.backend.application.ratelimit.RateLimitDecision;
import com.southwestasiafloat.backend.application.ratelimit.TokenBucketRateLimiter;
import com.southwestasiafloat.backend.application.ratelimit.TokenBucketRule;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnBean(RedissonClient.class)
public class RedisTokenBucketRateLimiter implements TokenBucketRateLimiter {

    private static final long TOKEN_SCALE = 1000L;

    private static final String CONSUME_SCRIPT = """
            local key = KEYS[1]
            local now_ms = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refill_tokens = tonumber(ARGV[3])
            local refill_period_ms = tonumber(ARGV[4])
            local requested = tonumber(ARGV[5])
            local ttl_ms = tonumber(ARGV[6])
            local scale = tonumber(ARGV[7])

            local values = redis.call('HMGET', key, 'tokens', 'refreshedAt')
            local tokens = tonumber(values[1])
            local refreshed_at = tonumber(values[2])

            if tokens == nil or refreshed_at == nil then
                tokens = capacity
                refreshed_at = now_ms
            else
                local elapsed_ms = math.max(0, now_ms - refreshed_at)
                if elapsed_ms > 0 then
                    local refill = math.floor(elapsed_ms * refill_tokens / refill_period_ms)
                    tokens = math.min(capacity, tokens + refill)
                    refreshed_at = now_ms
                end
            end

            local allowed = 0
            local retry_after_ms = 0
            if tokens >= requested then
                allowed = 1
                tokens = tokens - requested
            else
                local missing = requested - tokens
                retry_after_ms = math.ceil(missing * refill_period_ms / refill_tokens)
            end

            redis.call('HMSET', key, 'tokens', tokens, 'refreshedAt', refreshed_at)
            redis.call('PEXPIRE', key, ttl_ms)
            return {allowed, math.floor(tokens / scale), retry_after_ms}
            """;

    private static final String REFUND_SCRIPT = """
            local key = KEYS[1]
            local now_ms = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refill_tokens = tonumber(ARGV[3])
            local refill_period_ms = tonumber(ARGV[4])
            local returned = tonumber(ARGV[5])
            local ttl_ms = tonumber(ARGV[6])

            local values = redis.call('HMGET', key, 'tokens', 'refreshedAt')
            local tokens = tonumber(values[1])
            local refreshed_at = tonumber(values[2])

            if tokens == nil or refreshed_at == nil then
                tokens = capacity
                refreshed_at = now_ms
            else
                local elapsed_ms = math.max(0, now_ms - refreshed_at)
                if elapsed_ms > 0 then
                    local refill = math.floor(elapsed_ms * refill_tokens / refill_period_ms)
                    tokens = math.min(capacity, tokens + refill)
                    refreshed_at = now_ms
                end
            end

            tokens = math.min(capacity, tokens + returned)
            redis.call('HMSET', key, 'tokens', tokens, 'refreshedAt', refreshed_at)
            redis.call('PEXPIRE', key, ttl_ms)
            return tokens
            """;

    private final RedissonClient redissonClient;

    public RedisTokenBucketRateLimiter(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public RateLimitDecision tryConsume(String bucketKey, TokenBucketRule rule) {
        List<Object> result = runScript(
                CONSUME_SCRIPT,
                bucketKey,
                nowMs(),
                scaled(rule.capacity()),
                scaled(rule.refillTokens()),
                millis(rule.refillPeriod()),
                TOKEN_SCALE,
                millis(rule.idleTtl()),
                TOKEN_SCALE
        );

        long allowed = toLong(result.get(0));
        long remainingTokens = toLong(result.get(1));
        long retryAfterMs = toLong(result.get(2));
        if (allowed == 1L) {
            return RateLimitDecision.allowed(remainingTokens);
        }
        return RateLimitDecision.blocked(remainingTokens, Duration.ofMillis(Math.max(1, retryAfterMs)));
    }

    @Override
    public void refund(String bucketKey, TokenBucketRule rule) {
        runScript(
                REFUND_SCRIPT,
                bucketKey,
                nowMs(),
                scaled(rule.capacity()),
                scaled(rule.refillTokens()),
                millis(rule.refillPeriod()),
                TOKEN_SCALE,
                millis(rule.idleTtl())
        );
    }

    @SuppressWarnings("unchecked")
    private List<Object> runScript(String script, String bucketKey, Object... args) {
        return redissonClient.getScript(StringCodec.INSTANCE)
                .eval(
                        RScript.Mode.READ_WRITE,
                        script,
                        RScript.ReturnType.LIST,
                        List.of(bucketKey),
                        args
                );
    }

    private long nowMs() {
        return System.currentTimeMillis();
    }

    private long scaled(int tokens) {
        return Math.multiplyExact((long) tokens, TOKEN_SCALE);
    }

    private long millis(Duration duration) {
        return Math.max(1L, duration.toMillis());
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
