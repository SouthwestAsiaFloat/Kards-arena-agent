package com.southwestasiafloat.backend.infrastructure.lock;

/**
 * MySQL 会话锁实现。
 */

import com.southwestasiafloat.backend.config.ArenaSessionProperties;
import com.southwestasiafloat.backend.domain.gateway.SessionLockManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "mysql")
public class MySqlSessionLockManager implements SessionLockManager {

    private final MySqlNamedLockTemplate namedLockTemplate;
    private final ArenaSessionProperties sessionProperties;

    public MySqlSessionLockManager(MySqlNamedLockTemplate namedLockTemplate,
                                   ArenaSessionProperties sessionProperties) {
        this.namedLockTemplate = namedLockTemplate;
        this.sessionProperties = sessionProperties;
    }

    @Override
    public <T> T withSessionLock(String sessionId, Supplier<T> action) {
        if (sessionId == null || sessionId.isBlank()) {
            return action.get();
        }
        return namedLockTemplate.withLock(sessionProperties.getLockKeyPrefix() + ":" + sessionId, action);
    }
}
