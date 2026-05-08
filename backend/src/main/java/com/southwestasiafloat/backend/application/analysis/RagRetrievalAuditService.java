package com.southwestasiafloat.backend.application.analysis;

import com.southwestasiafloat.backend.application.analysis.model.KnowledgeRetrievalHit;
import com.southwestasiafloat.backend.application.analysis.model.KnowledgeRetrievalResult;
import com.southwestasiafloat.backend.application.analysis.model.RagRetrievalAuditEvent;
import com.southwestasiafloat.backend.application.analysis.model.RagRetrievalAuditHit;
import com.southwestasiafloat.backend.application.analysis.support.RagRetrievalAuditRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class RagRetrievalAuditService {

    private static final String TOOL_NAME = "retrieveStrategyKnowledge";

    private final RagRetrievalAuditRepository auditRepository;
    private final MeterRegistry meterRegistry;

    public RagRetrievalAuditService(RagRetrievalAuditRepository auditRepository,
                                    MeterRegistry meterRegistry) {
        this.auditRepository = auditRepository;
        this.meterRegistry = meterRegistry;
    }

    public void record(String analysisId,
                       String sessionId,
                       KnowledgeRetrievalResult result,
                       Duration duration) {
        try {
            RagRetrievalAuditEvent event = new RagRetrievalAuditEvent(
                    UUID.randomUUID().toString(),
                    analysisId,
                    sessionId,
                    TOOL_NAME,
                    buildQueryText(result.tags(), result.keywords()),
                    result.tags(),
                    result.keywords(),
                    result.requestedLimit(),
                    Math.max(0, duration.toMillis()),
                    Instant.now(),
                    buildHits(result.hits())
            );
            auditRepository.save(event);
            meterRegistry.counter("arena.rag.retrieval.events", "result", "saved").increment();
            meterRegistry.summary("arena.rag.retrieval.documents").record(result.hits().size());
            meterRegistry.timer("arena.rag.retrieval.duration").record(duration);
        } catch (Exception ex) {
            meterRegistry.counter("arena.rag.retrieval.events", "result", "failed").increment();
            log.warn("Failed to persist RAG retrieval audit event analysisId={} sessionId={}", analysisId, sessionId, ex);
        }
    }

    private List<RagRetrievalAuditHit> buildHits(List<KnowledgeRetrievalHit> hits) {
        List<RagRetrievalAuditHit> auditHits = new ArrayList<>();
        for (int index = 0; index < hits.size(); index++) {
            KnowledgeRetrievalHit hit = hits.get(index);
            auditHits.add(new RagRetrievalAuditHit(index + 1, hit.document(), hit.score()));
        }
        return auditHits;
    }

    private String buildQueryText(List<String> tags, List<String> keywords) {
        return "tags=" + String.join(",", safeList(tags))
                + "; keywords=" + String.join(",", safeList(keywords));
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
