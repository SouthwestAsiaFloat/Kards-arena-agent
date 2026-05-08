package com.southwestasiafloat.backend.application.analysis.support;

/**
 * 分析任务状态通知器，负责推送任务变化。
 */

import com.southwestasiafloat.backend.application.analysis.model.AsyncDraftAnalyzeJob;

public interface AnalyzeJobUpdateNotifier {
    void notifyJob(AsyncDraftAnalyzeJob job);
}
