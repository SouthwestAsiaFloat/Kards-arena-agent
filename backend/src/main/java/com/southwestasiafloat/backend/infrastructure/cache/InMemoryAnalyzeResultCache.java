package com.southwestasiafloat.backend.infrastructure.cache;

/**
 * 内存分析结果缓存实现。
 */

import com.southwestasiafloat.backend.config.ArenaAnalysisProperties;
import com.southwestasiafloat.backend.domain.gateway.AnalyzeResultCache;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryAnalyzeResultCache implements AnalyzeResultCache {

    private final ConcurrentMap<String, CacheEntry> store = new ConcurrentHashMap<>();
    private final Duration ttl;

    public InMemoryAnalyzeResultCache(ArenaAnalysisProperties analysisProperties) {
        this.ttl = analysisProperties.getCacheTtl();
    }

    @Override
    public Optional<DraftAnalyzeResponse> get(String key) {
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }

        if (entry.isExpired()) {
            store.remove(key, entry);
            return Optional.empty();
        }

        return Optional.of(entry.response());
    }

    @Override
    public void put(String key, DraftAnalyzeResponse response) {
        store.put(key, new CacheEntry(response, expiresAt()));
    }

    private Instant expiresAt() {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Instant.MAX;
        }
        return Instant.now().plus(ttl);
    }

    private record CacheEntry(DraftAnalyzeResponse response, Instant expiresAt) {
        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
