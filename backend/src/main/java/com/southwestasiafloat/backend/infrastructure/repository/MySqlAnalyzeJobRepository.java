package com.southwestasiafloat.backend.infrastructure.repository;

/**
 * MySQL 分析任务仓储实现。
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.application.analysis.model.AsyncDraftAnalyzeJob;
import com.southwestasiafloat.backend.application.analysis.support.AnalyzeJobRepository;
import com.southwestasiafloat.backend.config.ArenaOcrAsyncProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "mysql")
public class MySqlAnalyzeJobRepository implements AnalyzeJobRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public MySqlAnalyzeJobRepository(JdbcTemplate jdbcTemplate,
                                     ObjectMapper objectMapper,
                                     ArenaOcrAsyncProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.ttl = properties.getJobTtl();
    }

    @Override
    public void save(AsyncDraftAnalyzeJob job) {
        Instant now = Instant.now();
        Instant createdAt = job.getCreatedAt() != null ? job.getCreatedAt() : now;
        Instant updatedAt = job.getUpdatedAt() != null ? job.getUpdatedAt() : now;
        jdbcTemplate.update("""
                        INSERT INTO arena_analyze_jobs
                            (job_id, session_id, cache_key, status, payload, created_at, updated_at, expires_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            session_id = VALUES(session_id),
                            cache_key = VALUES(cache_key),
                            status = VALUES(status),
                            payload = VALUES(payload),
                            updated_at = VALUES(updated_at),
                            expires_at = VALUES(expires_at)
                        """,
                job.getJobId(),
                job.getSessionId(),
                job.getCacheKey(),
                job.getStatus().name(),
                serialize(job),
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt),
                toTimestamp(expiresAt(now)));
    }

    @Override
    public Optional<AsyncDraftAnalyzeJob> findById(String jobId) {
        deleteIfExpired(jobId);
        return jdbcTemplate.query("""
                        SELECT payload
                        FROM arena_analyze_jobs
                        WHERE job_id = ?
                          AND (expires_at IS NULL OR expires_at > ?)
                        """,
                ps -> {
                    ps.setString(1, jobId);
                    ps.setTimestamp(2, Timestamp.from(Instant.now()));
                },
                rs -> rs.next() ? Optional.of(deserialize(rs.getString("payload"))) : Optional.empty());
    }

    @Override
    public Collection<AsyncDraftAnalyzeJob> findAll() {
        return jdbcTemplate.query("""
                        SELECT payload
                        FROM arena_analyze_jobs
                        WHERE expires_at IS NULL OR expires_at > ?
                        """,
                ps -> ps.setTimestamp(1, Timestamp.from(Instant.now())),
                (rs, rowNum) -> deserialize(rs.getString("payload")));
    }

    private void deleteIfExpired(String jobId) {
        jdbcTemplate.update("""
                        DELETE FROM arena_analyze_jobs
                        WHERE job_id = ?
                          AND expires_at IS NOT NULL
                          AND expires_at <= ?
                        """,
                jobId,
                Timestamp.from(Instant.now()));
    }

    private String serialize(AsyncDraftAnalyzeJob job) {
        try {
            return objectMapper.writeValueAsString(job);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize analyze job " + job.getJobId(), ex);
        }
    }

    private AsyncDraftAnalyzeJob deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, AsyncDraftAnalyzeJob.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize analyze job", ex);
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
