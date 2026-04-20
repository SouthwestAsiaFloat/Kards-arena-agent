package com.southwestasiafloat.backend.domain.gateway;

import java.util.function.Supplier;

public interface AnalyzeRequestLockManager {

    <T> T withAnalyzeLock(String key, Supplier<T> action);
}
