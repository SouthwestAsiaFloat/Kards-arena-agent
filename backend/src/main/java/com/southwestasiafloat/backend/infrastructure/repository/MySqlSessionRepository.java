package com.southwestasiafloat.backend.infrastructure.repository;

/**
 * MySQL 会话仓储实现。
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.config.ArenaSessionProperties;
import com.southwestasiafloat.backend.domain.gateway.SessionRepository;
import com.southwestasiafloat.backend.domain.model.DraftSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "mysql")
public class MySqlSessionRepository implements SessionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public MySqlSessionRepository(JdbcTemplate jdbcTemplate,
                                  ObjectMapper objectMapper,
                                  ArenaSessionProperties sessionProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.ttl = sessionProperties.getTtl();
    }

    @Override
    public DraftSession save(DraftSession session) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        INSERT INTO arena_draft_sessions
                            (session_id, payload, created_at, updated_at, expires_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            payload = VALUES(payload),
                            updated_at = VALUES(updated_at),
                            expires_at = VALUES(expires_at)
                        """,
                session.getSessionId(),
                serialize(session),
                Timestamp.from(now),
                Timestamp.from(now),
                toTimestamp(expiresAt(now)));
        return session;
    }

    @Override
    public Optional<DraftSession> findById(String sessionId) {
        deleteIfExpired(sessionId);
        return jdbcTemplate.query("""
                        SELECT payload
                        FROM arena_draft_sessions
                        WHERE session_id = ?
                          AND (expires_at IS NULL OR expires_at > ?)
                        """,
                ps -> {
                    ps.setString(1, sessionId);
                    ps.setTimestamp(2, Timestamp.from(Instant.now()));
                },
                rs -> rs.next() ? Optional.of(deserialize(rs.getString("payload"))) : Optional.empty());
    }

    @Override
    public void deleteById(String sessionId) {
        jdbcTemplate.update("DELETE FROM arena_draft_sessions WHERE session_id = ?", sessionId);
    }

    @Override
    public boolean existsById(String sessionId) {
        return findById(sessionId).isPresent();
    }

    @Override
    public DraftSession get() {
        return jdbcTemplate.query("""
                        SELECT payload
                        FROM arena_draft_sessions
                        WHERE expires_at IS NULL OR expires_at > ?
                        ORDER BY updated_at DESC
                        LIMIT 1
                        """,
                ps -> ps.setTimestamp(1, Timestamp.from(Instant.now())),
                rs -> rs.next() ? deserialize(rs.getString("payload")) : null);
    }

    private void deleteIfExpired(String sessionId) {
        jdbcTemplate.update("""
                        DELETE FROM arena_draft_sessions
                        WHERE session_id = ?
                          AND expires_at IS NOT NULL
                          AND expires_at <= ?
                        """,
                sessionId,
                Timestamp.from(Instant.now()));
    }

    private String serialize(DraftSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize draft session " + session.getSessionId(), ex);
        }
    }

    private DraftSession deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, DraftSession.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize draft session", ex);
        }
    }

    private Instant expiresAt(Instant now) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return null;
        }
        return now.plus(ttl);
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
