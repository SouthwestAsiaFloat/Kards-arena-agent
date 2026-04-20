package com.southwestasiafloat.backend.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.domain.model.KnowledgeDocument;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    private static final String KNOWLEDGE_GLOB = "classpath*:knowledge-base/**/*.jsonl";

    private final ObjectMapper objectMapper;
    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    private volatile List<KnowledgeDocument> cachedDocuments;

    public KnowledgeBaseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<KnowledgeDocument> getAllDocuments() {
        List<KnowledgeDocument> snapshot = cachedDocuments;
        if (snapshot != null) {
            return snapshot;
        }

        synchronized (this) {
            if (cachedDocuments == null) {
                cachedDocuments = Collections.unmodifiableList(loadDocuments());
            }
            return cachedDocuments;
        }
    }

    public List<KnowledgeDocument> findByTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return List.of();
        }

        String normalized = tag.trim().toLowerCase(Locale.ROOT);
        return getAllDocuments().stream()
                .filter(document -> document.getTags() != null)
                .filter(document -> document.getTags().stream()
                        .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                        .anyMatch(normalized::equals))
                .collect(Collectors.toList());
    }

    public Map<String, List<KnowledgeDocument>> groupByCollection() {
        return getAllDocuments().stream()
                .collect(Collectors.groupingBy(
                        document -> document.getCollection() == null ? "default" : document.getCollection(),
                        Collectors.toList()
                ));
    }

    public List<KnowledgeDocument> retrieveRelevant(List<String> tags, List<String> keywords, int limit) {
        Set<String> normalizedTags = normalizeTokens(tags, false);
        Set<String> normalizedKeywords = normalizeTokens(keywords, true);
        int effectiveLimit = limit <= 0 ? 5 : limit;

        List<KnowledgeDocument> ranked = getAllDocuments().stream()
                .map(document -> new ScoredKnowledgeDocument(document, score(document, normalizedTags, normalizedKeywords)))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator
                        .comparingDouble(ScoredKnowledgeDocument::score).reversed()
                        .thenComparing(candidate -> candidate.document().getId(), Comparator.nullsLast(String::compareTo)))
                .limit(effectiveLimit)
                .map(ScoredKnowledgeDocument::document)
                .collect(Collectors.toList());

        if (!ranked.isEmpty()) {
            return ranked;
        }

        return getAllDocuments().stream()
                .filter(document -> document.getTags() != null)
                .filter(document -> document.getTags().stream()
                        .filter(Objects::nonNull)
                        .map(value -> value.trim().toLowerCase(Locale.ROOT))
                        .anyMatch(tag -> tag.equals("general") || tag.equals("draft")))
                .limit(effectiveLimit)
                .collect(Collectors.toList());
    }

    private List<KnowledgeDocument> loadDocuments() {
        try {
            Resource[] resources = resourceResolver.getResources(KNOWLEDGE_GLOB);
            List<KnowledgeDocument> documents = new ArrayList<>();

            for (Resource resource : resources) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) {
                            continue;
                        }
                        documents.add(objectMapper.readValue(trimmed, KnowledgeDocument.class));
                    }
                }
            }

            return documents;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load knowledge base documents", e);
        }
    }

    private Set<String> normalizeTokens(List<String> values, boolean filterShortKeywords) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }

        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .filter(value -> !filterShortKeywords || value.length() > 1)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private double score(KnowledgeDocument document, Set<String> tags, Set<String> keywords) {
        double score = 0;
        Set<String> documentTags = normalizeTokens(document.getTags(), false);

        for (String tag : tags) {
            if (documentTags.contains(tag)) {
                score += switch (tag) {
                    case "general", "draft" -> 2.5;
                    case "curve", "tempo", "removal", "guard", "aoe", "draw" -> 4.0;
                    default -> 5.0;
                };
            }
        }

        String title = normalizeText(document.getTitle());
        String summary = normalizeText(document.getSummary());
        String content = normalizeText(document.getContent());
        String metadata = normalizeText(document.getMetadata() == null ? "" : document.getMetadata().toString());

        for (String keyword : keywords) {
            if (title.contains(keyword)) {
                score += 5.0;
            } else if (summary.contains(keyword)) {
                score += 3.5;
            } else if (content.contains(keyword)) {
                score += 2.5;
            } else if (metadata.contains(keyword)) {
                score += 2.0;
            }
        }

        if ("arena-strategy-guides".equals(document.getCollection()) && !documentTags.isEmpty()) {
            score += 0.5;
        }
        if ("game-mechanics-glossary".equals(document.getCollection()) && !keywords.isEmpty()) {
            score += 0.5;
        }

        return score;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record ScoredKnowledgeDocument(KnowledgeDocument document, double score) {
    }
}
