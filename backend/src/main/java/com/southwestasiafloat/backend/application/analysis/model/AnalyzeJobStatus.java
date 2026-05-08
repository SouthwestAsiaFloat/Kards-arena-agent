package com.southwestasiafloat.backend.application.analysis.model;

/**
 * 异步分析任务状态枚举。
 */

public enum AnalyzeJobStatus {
    QUEUED,
    ANALYZING,
    COMPLETED,
    FAILED
}
