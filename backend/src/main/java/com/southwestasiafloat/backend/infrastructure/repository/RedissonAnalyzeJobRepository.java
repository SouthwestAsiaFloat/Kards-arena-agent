package com.southwestasiafloat.backend.infrastructure.repository;

/**
 * Redis 分析任务仓储实现。
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.application.analysis.model.AsyncDraftAnalyzeJob;
import com.southwestasiafloat.backend.application.analysis.support.AnalyzeJobRepository;
import com.southwestasiafloat.backend.config.ArenaOcrAsyncProperties;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "redis")
public class RedissonAnalyzeJobRepository implements AnalyzeJobRepository {

    private final RMapCache<String, String> jobs;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedissonAnalyzeJobRepository(RedissonClient redissonClient,
                                        ArenaOcrAsyncProperties properties,
                                        ObjectMapper objectMapper) {
        this.jobs = redissonClient.getMapCache(properties.getJobRedisMapName());
        this.objectMapper = objectMapper;
        this.ttl = properties.getJobTtl();
    }

    @Override
    public void save(AsyncDraftAnalyzeJob job) {
        String payload = serialize(job);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            jobs.put(job.getJobId(), payload);
        } else {
            jobs.put(job.getJobId(), payload, ttl.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public Optional<AsyncDraftAnalyzeJob> findById(String jobId) {
        return Optional.ofNullable(jobs.get(jobId)).map(this::deserialize);
    }

    @Override
    public Collection<AsyncDraftAnalyzeJob> findAll() {
        return jobs.readAllValues().stream()
                .map(this::deserialize)
                .filter(Objects::nonNull)
                .toList();
    }

    private String serialize(AsyncDraftAnalyzeJob job) {
        try {
            return objectMapper.writeValueAsString(job);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize analyze job " + job.getJobId(), ex);
        }
    }

    private AsyncDraftAnalyzeJob deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, AsyncDraftAnalyzeJob.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize analyze job", ex);
        }
    }
}
