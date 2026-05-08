package com.southwestasiafloat.backend.domain.model;

/**
 * 草稿会话领域模型，保存当前对局状态。
 */

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class DraftSession {

    private String sessionId;
    private List<Card> pickedCards = new ArrayList<>();
    private List<DraftHistoryEntry> history = new ArrayList<>();
    private DeckState deckState;
    private Integer currentPickNo = 1;

    public DraftSession(String sessionId) {
        this.sessionId = sessionId;
        this.currentPickNo = 1;
    }

    public void addPickedCard(Card pickedCard) {
        this.pickedCards.add(pickedCard);
        this.currentPickNo++;
    }

    public void addHistoryEntry(DraftHistoryEntry historyEntry) {
        this.history.add(0, historyEntry);
    }

    public void confirmLatestPick(Card pickedCard) {
        if (this.history.isEmpty()) {
            return;
        }

        DraftHistoryEntry latestHistory = this.history.get(0);
        latestHistory.setPickedCard(pickedCard);
        latestHistory.setStatus("CONFIRMED");
    }
}
