package com.southwestasiafloat.backend.domain.gateway;

/**
 * 分析请求锁接口。
 */

import java.util.function.Supplier;

public interface AnalyzeRequestLockManager {

    <T> T withAnalyzeLock(String key, Supplier<T> action);
}
