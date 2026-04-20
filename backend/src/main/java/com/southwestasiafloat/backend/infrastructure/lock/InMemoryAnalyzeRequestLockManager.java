package com.southwestasiafloat.backend.infrastructure.lock;

import com.southwestasiafloat.backend.domain.gateway.AnalyzeRequestLockManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryAnalyzeRequestLockManager implements AnalyzeRequestLockManager {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public <T> T withAnalyzeLock(String key, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
