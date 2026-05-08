package com.southwestasiafloat.backend.application.analysis;

/**
 * 应用层服务，负责串联校验、缓存、锁、消息和领域服务。
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.domain.model.KnowledgeDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseServiceTest {

    @Test
    void shouldLoadStrategyGuideDocuments() {
        KnowledgeBaseService service = new KnowledgeBaseService(new ObjectMapper());

        List<KnowledgeDocument> documents = service.getAllDocuments();

        assertFalse(documents.isEmpty());
        assertTrue(documents.stream().anyMatch(document -> "kb-arena-rules-001".equals(document.getId())));
        assertTrue(documents.stream().anyMatch(document -> document.getTags().contains("japan")));
    }

    @Test
    void shouldRetrieveRelevantDocumentsForDraftContext() {
        KnowledgeBaseService service = new KnowledgeBaseService(new ObjectMapper());

        List<KnowledgeDocument> documents = service.retrieveRelevant(
                List.of("general", "draft", "ussr", "guard"),
                List.of("\u82cf\u8054", "\u5b88\u62a4"),
                5
        );

        assertFalse(documents.isEmpty());
        assertTrue(documents.stream()
                .anyMatch(document -> document.getTags().contains("ussr")
                        || document.getTags().contains("general")
                        || document.getTags().contains("guard")));
    }
}
