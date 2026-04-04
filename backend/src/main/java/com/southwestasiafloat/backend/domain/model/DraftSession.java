package com.southwestasiafloat.backend.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 目前已选卡牌列表和当前牌堆状态
@Data
public class DraftSession {

    private String sessionId;
    private List<Card> pickedCards = new ArrayList<>();
    private List<DraftHistoryEntry> history = new ArrayList<>();
    private DeckState deckState;
    private Integer currentPickNo;

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
        latestHistory.setStatus("已确认");
    }
}

