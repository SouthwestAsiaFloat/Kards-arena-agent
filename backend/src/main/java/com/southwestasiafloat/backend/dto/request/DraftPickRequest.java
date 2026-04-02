package com.southwestasiafloat.backend.dto.request;

import com.southwestasiafloat.backend.domain.model.Card;

public class DraftPickRequest {

    private String sessionId;
    private Card pickedCard;

    public DraftPickRequest() {
    }

    public DraftPickRequest(String sessionId, Card pickedCard) {
        this.sessionId = sessionId;
        this.pickedCard = pickedCard;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Card getPickedCard() {
        return pickedCard;
    }

    public void setPickedCard(Card pickedCard) {
        this.pickedCard = pickedCard;
    }
}