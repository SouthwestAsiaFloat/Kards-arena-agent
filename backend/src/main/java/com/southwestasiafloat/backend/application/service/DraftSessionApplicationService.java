package com.southwestasiafloat.backend.application.service;

import com.southwestasiafloat.backend.domain.gateway.SessionLockManager;
import com.southwestasiafloat.backend.domain.gateway.SessionRepository;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.DeckState;
import com.southwestasiafloat.backend.domain.model.DraftSession;
import com.southwestasiafloat.backend.domain.service.DeckStateAnalyzer;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DraftSessionApplicationService {

    private final SessionRepository sessionRepository;
    private final DeckStateAnalyzer deckStateAnalyzer;
    private final SessionLockManager sessionLockManager;

    public DraftSessionApplicationService(DeckStateAnalyzer deckStateAnalyzer,
                                          SessionRepository sessionRepository,
                                          SessionLockManager sessionLockManager) {
        this.sessionRepository = sessionRepository;
        this.deckStateAnalyzer = deckStateAnalyzer;
        this.sessionLockManager = sessionLockManager;
    }

    public DraftSession createSession() {
        String sessionId = UUID.randomUUID().toString();
        DraftSession session = new DraftSession(sessionId);
        return sessionRepository.save(session);
    }

    public DraftSession getSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("DraftSession not found, sessionId=" + sessionId));
    }

    public DraftSession pickCard(String sessionId, Card pickedCard) {
        return sessionLockManager.withSessionLock(sessionId, () -> {
            DraftSession session = getSession(sessionId);

            session.confirmLatestPick(pickedCard);
            session.addPickedCard(pickedCard);

            DeckState newDeckState = deckStateAnalyzer.analyze(session.getPickedCards());
            session.setDeckState(newDeckState);

            return sessionRepository.save(session);
        });
    }

    public void saveSession(DraftSession session) {
        sessionLockManager.runWithSessionLock(session.getSessionId(), () -> sessionRepository.save(session));
    }

    public void removeSession(String sessionId) {
        sessionLockManager.runWithSessionLock(sessionId, () -> sessionRepository.deleteById(sessionId));
    }

    public boolean exists(String sessionId) {
        return sessionRepository.existsById(sessionId);
    }
}
