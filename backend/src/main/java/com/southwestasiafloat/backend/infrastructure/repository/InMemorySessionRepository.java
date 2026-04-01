package com.southwestasiafloat.backend.infrastructure.repository;

import com.southwestasiafloat.backend.domain.model.DraftSession;
import org.springframework.stereotype.Repository;


@Repository
public class InMemorySessionRepository {

    private  DraftSession currentSession;

    public DraftSession get() {
        return currentSession;
    }

    public void save(DraftSession session) {
        this.currentSession = session;
    }

    public void clear() {
        this.currentSession = null;
    }
}