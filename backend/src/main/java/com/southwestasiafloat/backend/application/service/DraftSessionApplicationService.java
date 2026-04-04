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

    /**
     * 开始一局新的 draft
     */
    public DraftSession createSession() {
        String sessionId = UUID.randomUUID().toString();
        DraftSession session = new DraftSession(sessionId);
        return inMemorySessionRepository.save(session);
    }

    /**
     * 获取当前 session
     */
    public DraftSession getSession(String sessionId) {
        return inMemorySessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("找不到对应的 DraftSession, sessionId=" + sessionId));
    }

    /**
     * 用户确认抓了一张牌后，更新 session
     */
    public DraftSession pickCard(String sessionId, Card pickedCard) {
        DraftSession session = getSession(sessionId);

        // 加入已抓牌
        session.addPickedCard(pickedCard);

        // 重新分析当前卡组状态
        DeckState newDeckState = deckStateAnalyzer.analyze(session.getPickedCards());
        session.setDeckState(newDeckState);

        return inMemorySessionRepository.save(session);
    }

    /**
     * 手动覆盖 session（有时候你想整体更新）
     */
    public void saveSession(DraftSession session) {
        inMemorySessionRepository.save(session);
    }

    /**
     * 结束一局，删除内存中的 session
     */
    public void removeSession(String sessionId) {
        inMemorySessionRepository.deleteById(sessionId);
    }

    /**
     * 判断 session 是否存在
     */
    public boolean exists(String sessionId) {
        return inMemorySessionRepository.existsById(sessionId);
    }
}
