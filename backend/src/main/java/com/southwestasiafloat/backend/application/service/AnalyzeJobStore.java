package com.southwestasiafloat.backend.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.config.ArenaOcrAsyncProperties;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Component
public class AnalyzeJobStore {

    private final ConcurrentMap<String, AsyncDraftAnalyzeJob> jobs = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Duration jobTtl;
    private final RMapCache<String, String> redisJobs;

    public AnalyzeJobStore(ArenaOcrAsyncProperties properties,
                           ObjectMapper objectMapper,
                           ObjectProvider<RedissonClient> redissonClientProvider) {
        this.objectMapper = objectMapper;
        this.jobTtl = properties.getJobTtl();
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        this.redisJobs = redissonClient != null
                ? redissonClient.getMapCache(properties.getJobRedisMapName())
                : null;
    }

    public AsyncDraftAnalyzeJob createQueued(String sessionId, String cacheKey) {
        String jobId = UUID.randomUUID().toString();
        AsyncDraftAnalyzeJob job = new AsyncDraftAnalyzeJob(jobId, sessionId, cacheKey, AnalyzeJobStatus.QUEUED);
        save(job);
        return job;
    }

    public AsyncDraftAnalyzeJob createCompleted(String sessionId,
                                                String cacheKey,
                                                DraftAnalyzeResponse response) {
        AsyncDraftAnalyzeJob job = createQueued(sessionId, cacheKey);
        job.markCompleted(response);
        save(job);
        return job;
    }

    public Optional<AsyncDraftAnalyzeJob> findById(String jobId) {
        if (redisJobs != null) {
            return Optional.ofNullable(redisJobs.get(jobId))
                    .map(this::deserialize);
        }
        return Optional.ofNullable(jobs.get(jobId));
    }

    public AsyncDraftAnalyzeJob require(String jobId) {
        return findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown analyze job: " + jobId));
    }

    public long countActiveJobs() {
        return readAllJobs().stream()
                .filter(this::isActive)
                .count();
    }

    public long countActiveJobsForSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }
        return readAllJobs().stream()
                .filter(this::isActive)
                .filter(job -> sessionId.equals(job.getSessionId()))
                .count();
    }

    public List<AsyncDraftAnalyzeJob> findStaleActiveJobs(Duration staleAfter) {
        if (staleAfter == null || staleAfter.isZero() || staleAfter.isNegative()) {
            return List.of();
        }

        Instant cutoff = Instant.now().minus(staleAfter);
        return readAllJobs().stream()
                .filter(this::isActive)
                .filter(job -> job.getUpdatedAt() != null && job.getUpdatedAt().isBefore(cutoff))
                .toList();
    }

    public void save(AsyncDraftAnalyzeJob job) {
        if (redisJobs != null) {
            putRedis(job.getJobId(), serialize(job));
            return;
        }
        jobs.put(job.getJobId(), job);
    }

    private void putRedis(String jobId, String serializedJob) {
        if (jobTtl == null || jobTtl.isZero() || jobTtl.isNegative()) {
            redisJobs.put(jobId, serializedJob);
        } else {
            redisJobs.put(jobId, serializedJob, jobTtl.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private String serialize(AsyncDraftAnalyzeJob job) {
        try {
            return objectMapper.writeValueAsString(job);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize analyze job: " + job.getJobId(), ex);
        }
    }

    private AsyncDraftAnalyzeJob deserialize(String serializedJob) {
        try {
            return objectMapper.readValue(serializedJob, AsyncDraftAnalyzeJob.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize analyze job", ex);
        }
    }

    private Collection<AsyncDraftAnalyzeJob> readAllJobs() {
        if (redisJobs != null) {
            return redisJobs.readAllValues().stream()
                    .map(this::deserialize)
                    .filter(Objects::nonNull)
                    .toList();
        }
        return jobs.values();
    }

    private boolean isActive(AsyncDraftAnalyzeJob job) {
        return job != null
                && (job.getStatus() == AnalyzeJobStatus.QUEUED
                || job.getStatus() == AnalyzeJobStatus.ANALYZING);
    }
}
