package com.southwestasiafloat.backend.infrastructure.lock;

/**
 * Redis 会话锁实现。
 */

import com.southwestasiafloat.backend.config.ArenaSessionProperties;
import com.southwestasiafloat.backend.domain.gateway.SessionLockManager;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "redis")
public class RedissonSessionLockManager implements SessionLockManager {

    private final RedissonClient redissonClient;
    private final ArenaSessionProperties sessionProperties;

    public RedissonSessionLockManager(RedissonClient redissonClient,
                                      ArenaSessionProperties sessionProperties) {
        this.redissonClient = redissonClient;
        this.sessionProperties = sessionProperties;
    }

    @Override
    public <T> T withSessionLock(String sessionId, Supplier<T> action) {
        if (sessionId == null || sessionId.isBlank()) {
            return action.get();
        }

        RLock lock = redissonClient.getLock(buildLockKey(sessionId));
        // 这里不设置 leaseTime，让 Redisson watchdog 自动续约锁。
        lock.lock();
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String buildLockKey(String sessionId) {
        return sessionProperties.getLockKeyPrefix() + ":" + sessionId;
    }
}
