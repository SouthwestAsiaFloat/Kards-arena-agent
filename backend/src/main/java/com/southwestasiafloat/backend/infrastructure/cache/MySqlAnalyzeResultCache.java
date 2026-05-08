package com.southwestasiafloat.backend.infrastructure.cache;

/**
 * MySQL 分析结果缓存实现。
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.config.ArenaAnalysisProperties;
import com.southwestasiafloat.backend.domain.gateway.AnalyzeResultCache;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "mysql")
public class MySqlAnalyzeResultCache implements AnalyzeResultCache {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public MySqlAnalyzeResultCache(JdbcTemplate jdbcTemplate,
                                   ObjectMapper objectMapper,
                                   ArenaAnalysisProperties analysisProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.ttl = analysisProperties.getCacheTtl();
    }

    @Override
    public Optional<DraftAnalyzeResponse> get(String key) {
        deleteExpiredKey(key);
        return jdbcTemplate.query("""
                        SELECT payload
                        FROM arena_analyze_result_cache
                        WHERE cache_key = ?
                          AND (expires_at IS NULL OR expires_at > ?)
                        """,
                ps -> {
                    ps.setString(1, key);
                    ps.setTimestamp(2, Timestamp.from(Instant.now()));
                },
                rs -> rs.next() ? Optional.of(deserialize(rs.getString("payload"))) : Optional.empty());
    }

    @Override
    public void put(String key, DraftAnalyzeResponse response) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        INSERT INTO arena_analyze_result_cache
                            (cache_key, payload, created_at, expires_at)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            payload = VALUES(payload),
                            created_at = VALUES(created_at),
                            expires_at = VALUES(expires_at)
                        """,
                key,
                serialize(response),
                Timestamp.from(now),
                toTimestamp(expiresAt(now)));
    }

    private void deleteExpiredKey(String key) {
        jdbcTemplate.update("""
                        DELETE FROM arena_analyze_result_cache
                        WHERE cache_key = ?
                          AND expires_at IS NOT NULL
                          AND expires_at <= ?
                        """,
                key,
                Timestamp.from(Instant.now()));
    }

    private String serialize(DraftAnalyzeResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize analyze response", ex);
        }
    }

    private DraftAnalyzeResponse deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, DraftAnalyzeResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize analyze response", ex);
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
