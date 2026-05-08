package com.southwestasiafloat.backend.infrastructure.repository;

/**
 * 内存会话仓储实现。
 */

import com.southwestasiafloat.backend.domain.gateway.SessionRepository;
import com.southwestasiafloat.backend.domain.model.DraftSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "in-memory", matchIfMissing = true)
public class InMemorySessionRepository implements SessionRepository {

    private final Map<String, DraftSession> store = new ConcurrentHashMap<>();

    @Override
    public DraftSession save(DraftSession session) {
        store.put(session.getSessionId(), session);
        return session;
    }

    @Override
    public Optional<DraftSession> findById(String sessionId) {
        return Optional.ofNullable(store.get(sessionId));
    }

    @Override
    public void deleteById(String sessionId) {
        store.remove(sessionId);
    }

    @Override
    public boolean existsById(String sessionId) {
        return store.containsKey(sessionId);
    }

    // 兼容旧调用：返回任意一个现存 session（当前项目默认单局进行）
    @Override
    public DraftSession get() {
        return store.values().stream().findFirst().orElse(null);
    }

    // 兼容旧调用：清空内存会话
    public void clear() {
        store.clear();
    }
}
