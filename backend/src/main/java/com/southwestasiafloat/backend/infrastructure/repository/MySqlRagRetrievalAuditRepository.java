package com.southwestasiafloat.backend.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.application.analysis.model.RagRetrievalAuditEvent;
import com.southwestasiafloat.backend.application.analysis.model.RagRetrievalAuditHit;
import com.southwestasiafloat.backend.application.analysis.support.RagRetrievalAuditRepository;
import com.southwestasiafloat.backend.domain.model.KnowledgeDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.List;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class MySqlRagRetrievalAuditRepository implements RagRetrievalAuditRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public MySqlRagRetrievalAuditRepository(JdbcTemplate jdbcTemplate,
                                            TransactionTemplate transactionTemplate,
                                            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(RagRetrievalAuditEvent event) {
        transactionTemplate.executeWithoutResult(ignored -> {
            insertEvent(event);
            insertHits(event);
        });
    }

    private void insertEvent(RagRetrievalAuditEvent event) {
        jdbcTemplate.update(
                """
                        INSERT INTO arena_rag_retrieval_events
                            (event_id, analysis_id, session_id, tool_name, query_text, tags_json,
                             keywords_json, requested_limit, retrieved_count, duration_ms, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                event.eventId(),
                event.analysisId(),
                event.sessionId(),
                event.toolName(),
                event.queryText(),
                toJson(event.tags()),
                toJson(event.keywords()),
                event.requestedLimit(),
                event.hits().size(),
                event.durationMs(),
                Timestamp.from(event.createdAt())
        );
    }

    private void insertHits(RagRetrievalAuditEvent event) {
        for (RagRetrievalAuditHit hit : event.hits()) {
            KnowledgeDocument document = hit.document();
            jdbcTemplate.update(
                    """
                            INSERT INTO arena_rag_retrieval_hits
                                (event_id, rank_no, document_id, collection, title, score, snippet,
                                 source_title, source_url, tags_json, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    event.eventId(),
                    hit.rankNo(),
                    document.getId(),
                    document.getCollection(),
                    document.getTitle(),
                    hit.score(),
                    truncate(document.getContent(), 800),
                    document.getSourceTitle(),
                    document.getSourceUrl(),
                    toJson(document.getTags()),
                    Timestamp.from(event.createdAt())
            );
        }
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize RAG audit JSON", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
