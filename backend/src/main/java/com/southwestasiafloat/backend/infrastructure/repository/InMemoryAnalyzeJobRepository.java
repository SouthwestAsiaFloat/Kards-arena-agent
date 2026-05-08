package com.southwestasiafloat.backend.infrastructure.repository;

/**
 * 内存分析任务仓储实现。
 */

import com.southwestasiafloat.backend.application.analysis.model.AsyncDraftAnalyzeJob;
import com.southwestasiafloat.backend.application.analysis.support.AnalyzeJobRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryAnalyzeJobRepository implements AnalyzeJobRepository {

    private final ConcurrentMap<String, AsyncDraftAnalyzeJob> jobs = new ConcurrentHashMap<>();

    @Override
    public void save(AsyncDraftAnalyzeJob job) {
        jobs.put(job.getJobId(), job);
    }

    @Override
    public Optional<AsyncDraftAnalyzeJob> findById(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public Collection<AsyncDraftAnalyzeJob> findAll() {
        return jobs.values();
    }
}
