package com.southwestasiafloat.backend.infrastructure.repository;

import com.southwestasiafloat.backend.application.analysis.model.RagRetrievalAuditEvent;
import com.southwestasiafloat.backend.application.analysis.support.RagRetrievalAuditRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(RagRetrievalAuditRepository.class)
public class NoopRagRetrievalAuditRepository implements RagRetrievalAuditRepository {

    @Override
    public void save(RagRetrievalAuditEvent event) {
        // Database audit is optional; this no-op keeps local in-memory runs lightweight.
    }
}
