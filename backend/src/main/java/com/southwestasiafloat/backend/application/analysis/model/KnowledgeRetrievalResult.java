package com.southwestasiafloat.backend.application.analysis.model;

import com.southwestasiafloat.backend.domain.model.KnowledgeDocument;

import java.util.List;

public record KnowledgeRetrievalResult(
        List<String> tags,
        List<String> keywords,
        int requestedLimit,
        List<KnowledgeRetrievalHit> hits
) {

    public List<KnowledgeDocument> documents() {
        return hits.stream()
                .map(KnowledgeRetrievalHit::document)
                .toList();
    }
}
