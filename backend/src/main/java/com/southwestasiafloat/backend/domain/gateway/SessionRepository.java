package com.southwestasiafloat.backend.domain.gateway;

import com.southwestasiafloat.backend.domain.model.DraftSession;

import java.util.Optional;

public interface SessionRepository {

    DraftSession save(DraftSession session);

    Optional<DraftSession> findById(String sessionId);

    void deleteById(String sessionId);

    boolean existsById(String sessionId);

    DraftSession get();
}

