package com.southwestasiafloat.backend.infrastructure.repository;

import com.southwestasiafloat.backend.config.ArenaSessionProperties;
import com.southwestasiafloat.backend.domain.gateway.SessionRepository;
import com.southwestasiafloat.backend.domain.model.DraftSession;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "redis")
public class RedissonSessionRepository implements SessionRepository {

    private final RMapCache<String, DraftSession> sessions;
    private final Duration ttl;

    public RedissonSessionRepository(RedissonClient redissonClient,
                                     ArenaSessionProperties sessionProperties) {
        this.sessions = redissonClient.getMapCache(sessionProperties.getRedisMapName());
        this.ttl = sessionProperties.getTtl();
    }

    @Override
    public DraftSession save(DraftSession session) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            sessions.put(session.getSessionId(), session);
        } else {
            sessions.put(session.getSessionId(), session, ttl.toMillis(), TimeUnit.MILLISECONDS);
        }
        return session;
    }

    @Override
    public Optional<DraftSession> findById(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public void deleteById(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public boolean existsById(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    @Override
    public DraftSession get() {
        return sessions.readAllValues().stream().findFirst().orElse(null);
    }
}
