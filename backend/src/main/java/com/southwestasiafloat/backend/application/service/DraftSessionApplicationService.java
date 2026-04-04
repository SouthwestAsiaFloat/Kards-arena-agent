package com.southwestasiafloat.backend.application.service;

import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.DeckState;
import com.southwestasiafloat.backend.domain.model.DraftSession;
import com.southwestasiafloat.backend.domain.service.DeckStateAnalyzer;
import com.southwestasiafloat.backend.infrastructure.repository.InMemorySessionRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DraftSessionApplicationService {

    private final InMemorySessionRepository inMemorySessionRepository;
    private final DeckStateAnalyzer deckStateAnalyzer;

    public DraftSessionApplicationService(DeckStateAnalyzer deckStateAnalyzer,
                                          InMemorySessionRepository inMemorySessionRepository) {
        this.inMemorySessionRepository = inMemorySessionRepository;
        this.deckStateAnalyzer = deckStateAnalyzer;
    }

    public DraftSession createSession() {
        String sessionId = UUID.randomUUID().toString();
        DraftSession session = new DraftSession(sessionId);
        return inMemorySessionRepository.save(session);
    }

    public DraftSession getSession(String sessionId) {
        return inMemorySessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("DraftSession not found, sessionId=" + sessionId));
    }

    public DraftSession pickCard(String sessionId, Card pickedCard) {
        DraftSession session = getSession(sessionId);

        session.confirmLatestPick(pickedCard);
        session.addPickedCard(pickedCard);

        DeckState newDeckState = deckStateAnalyzer.analyze(session.getPickedCards());
        session.setDeckState(newDeckState);

        return inMemorySessionRepository.save(session);
    }

    public void saveSession(DraftSession session) {
        inMemorySessionRepository.save(session);
    }

    public void removeSession(String sessionId) {
        inMemorySessionRepository.deleteById(sessionId);
    }

    public boolean exists(String sessionId) {
        return inMemorySessionRepository.existsById(sessionId);
    }
}
