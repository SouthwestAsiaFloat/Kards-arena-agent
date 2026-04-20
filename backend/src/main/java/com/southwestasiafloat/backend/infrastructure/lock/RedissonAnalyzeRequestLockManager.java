package com.southwestasiafloat.backend.infrastructure.lock;

import com.southwestasiafloat.backend.config.ArenaAnalysisProperties;
import com.southwestasiafloat.backend.domain.gateway.AnalyzeRequestLockManager;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "redis")
public class RedissonAnalyzeRequestLockManager implements AnalyzeRequestLockManager {

    private final RedissonClient redissonClient;
    private final ArenaAnalysisProperties analysisProperties;

    public RedissonAnalyzeRequestLockManager(RedissonClient redissonClient,
                                             ArenaAnalysisProperties analysisProperties) {
        this.redissonClient = redissonClient;
        this.analysisProperties = analysisProperties;
    }

    @Override
    public <T> T withAnalyzeLock(String key, Supplier<T> action) {
        RLock lock = redissonClient.getLock(analysisProperties.getLockKeyPrefix() + ":" + key);
        lock.lock();
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
