package com.southwestasiafloat.backend.application.analysis.model;

import com.southwestasiafloat.backend.domain.model.KnowledgeDocument;

public record RagRetrievalAuditHit(
        int rankNo,
        KnowledgeDocument document,
        double score
) {
}
