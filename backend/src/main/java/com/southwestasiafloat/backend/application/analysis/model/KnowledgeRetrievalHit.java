package com.southwestasiafloat.backend.application.analysis.model;

import com.southwestasiafloat.backend.domain.model.KnowledgeDocument;

public record KnowledgeRetrievalHit(
        KnowledgeDocument document,
        double score,
        boolean fallback
) {
}
