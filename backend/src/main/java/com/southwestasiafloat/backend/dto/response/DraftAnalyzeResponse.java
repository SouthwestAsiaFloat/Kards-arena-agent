package com.southwestasiafloat.backend.dto.response;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.FinalDecision;

import java.util.List;

public class DraftAnalyzeResponse {
    private List<Card> offeredCards;
    private FinalDecision decision;

    public DraftAnalyzeResponse(List<Card> offeredCards, FinalDecision decision) {
        this.offeredCards = offeredCards;
        this.decision = decision;
    }

    public FinalDecision getDecision() {
        return decision;
    }

    public List<Card> getOfferedCards() {
        return offeredCards;
    }
}
