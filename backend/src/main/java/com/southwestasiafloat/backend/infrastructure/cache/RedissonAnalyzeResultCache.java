package com.southwestasiafloat.backend.infrastructure.cache;

/**
 * Redis 分析结果缓存实现。
 */

import com.southwestasiafloat.backend.config.ArenaAnalysisProperties;
import com.southwestasiafloat.backend.domain.gateway.AnalyzeResultCache;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "redis")
public class RedissonAnalyzeResultCache implements AnalyzeResultCache {

    private final RMapCache<String, DraftAnalyzeResponse> cache;
    private final Duration ttl;

    public RedissonAnalyzeResultCache(RedissonClient redissonClient,
                                      ArenaAnalysisProperties analysisProperties) {
        this.cache = redissonClient.getMapCache(analysisProperties.getRedisMapName());
        this.ttl = analysisProperties.getCacheTtl();
    }

    @Override
    public Optional<DraftAnalyzeResponse> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    @Override
    public void put(String key, DraftAnalyzeResponse response) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            cache.put(key, response);
        } else {
            cache.put(key, response, ttl.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
