package com.southwestasiafloat.backend.application.analysis.support;

import com.southwestasiafloat.backend.application.analysis.model.RagRetrievalAuditEvent;

public interface RagRetrievalAuditRepository {

    void save(RagRetrievalAuditEvent event);
}
