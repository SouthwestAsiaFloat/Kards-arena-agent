package com.southwestasiafloat.backend.dto.response;

/**
 * 草稿分析响应 DTO，返回推荐卡牌与决策信息。
 */

import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.FinalDecision;

import java.util.List;

public class DraftAnalyzeResponse {

    private List<Card> offeredCards;
    private FinalDecision decision;

    public DraftAnalyzeResponse() {
    }

    public DraftAnalyzeResponse(List<Card> offeredCards, FinalDecision decision) {
        this.offeredCards = offeredCards;
        this.decision = decision;
    }

    public FinalDecision getDecision() {
        return decision;
    }

    public void setDecision(FinalDecision decision) {
        this.decision = decision;
    }

    public List<Card> getOfferedCards() {
        return offeredCards;
    }

    public void setOfferedCards(List<Card> offeredCards) {
        this.offeredCards = offeredCards;
    }
}
