package com.southwestasiafloat.backend.application.analysis.model;

import java.time.Instant;
import java.util.List;

public record RagRetrievalAuditEvent(
        String eventId,
        String analysisId,
        String sessionId,
        String toolName,
        String queryText,
        List<String> tags,
        List<String> keywords,
        int requestedLimit,
        long durationMs,
        Instant createdAt,
        List<RagRetrievalAuditHit> hits
) {
}
