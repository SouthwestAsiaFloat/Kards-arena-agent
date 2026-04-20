package com.southwestasiafloat.backend.infrastructure.lock;

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
        // Acquire without leaseTime so Redisson watchdog can renew the lock automatically.
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
