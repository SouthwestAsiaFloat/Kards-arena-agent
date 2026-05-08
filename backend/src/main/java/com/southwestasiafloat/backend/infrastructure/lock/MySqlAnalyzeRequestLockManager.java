package com.southwestasiafloat.backend.infrastructure.lock;

/**
 * MySQL 分析请求锁实现。
 */

import com.southwestasiafloat.backend.config.ArenaAnalysisProperties;
import com.southwestasiafloat.backend.domain.gateway.AnalyzeRequestLockManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "mysql")
public class MySqlAnalyzeRequestLockManager implements AnalyzeRequestLockManager {

    private final MySqlNamedLockTemplate namedLockTemplate;
    private final ArenaAnalysisProperties analysisProperties;

    public MySqlAnalyzeRequestLockManager(MySqlNamedLockTemplate namedLockTemplate,
                                          ArenaAnalysisProperties analysisProperties) {
        this.namedLockTemplate = namedLockTemplate;
        this.analysisProperties = analysisProperties;
    }

    @Override
    public <T> T withAnalyzeLock(String key, Supplier<T> action) {
        return namedLockTemplate.withLock(analysisProperties.getLockKeyPrefix() + ":" + key, action);
    }
}
