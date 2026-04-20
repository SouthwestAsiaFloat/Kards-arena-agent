package com.southwestasiafloat.backend.domain.gateway;

import java.util.function.Supplier;

public interface SessionLockManager {

    <T> T withSessionLock(String sessionId, Supplier<T> action);

    default void runWithSessionLock(String sessionId, Runnable action) {
        withSessionLock(sessionId, () -> {
            action.run();
            return null;
        });
    }
}
