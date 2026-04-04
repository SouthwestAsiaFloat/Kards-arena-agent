package com.southwestasiafloat.backend.infrastructure.repository;

import com.southwestasiafloat.backend.domain.model.DraftSession;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemorySessionRepository {

    private final Map<String, DraftSession> store = new ConcurrentHashMap<>();

    public DraftSession save(DraftSession session) {
        store.put(session.getSessionId(), session);
        return session;
    }

    public Optional<DraftSession> findById(String sessionId) {
        return Optional.ofNullable(store.get(sessionId));
    }

    public void deleteById(String sessionId) {
        store.remove(sessionId);
    }

    public boolean existsById(String sessionId) {
        return store.containsKey(sessionId);
    }

    // 兼容旧调用：返回任意一个现存 session（当前项目默认单局进行）
    public DraftSession get() {
        return store.values().stream().findFirst().orElse(null);
    }

    // 兼容旧调用：清空内存会话
    public void clear() {
        store.clear();
    }
}