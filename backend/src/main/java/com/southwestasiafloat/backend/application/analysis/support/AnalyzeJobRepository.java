package com.southwestasiafloat.backend.application.analysis.support;

/**
 * 分析任务仓储接口。
 */

import com.southwestasiafloat.backend.application.analysis.model.AsyncDraftAnalyzeJob;
import java.util.Collection;
import java.util.Optional;

public interface AnalyzeJobRepository {

    void save(AsyncDraftAnalyzeJob job);

    Optional<AsyncDraftAnalyzeJob> findById(String jobId);

    Collection<AsyncDraftAnalyzeJob> findAll();
}
