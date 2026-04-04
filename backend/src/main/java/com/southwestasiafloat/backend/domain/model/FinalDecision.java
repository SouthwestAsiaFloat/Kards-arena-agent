package com.southwestasiafloat.backend.domain.model;

import lombok.Data;

@Data
public class FinalDecision {

    private CardEvaluationResult recommendedCard;
    private String llmReason;
    private String decisionSource; // "rule" or "llm"
    private Double finalScore;

    public FinalDecision() {
    }

    public FinalDecision(CardEvaluationResult recommendedCard, String llmReason,
                         String decisionSource, Double finalScore) {
        this.recommendedCard = recommendedCard;
        this.llmReason = llmReason;
        this.decisionSource = decisionSource;
        this.finalScore = finalScore;
    }

    public CardEvaluationResult getRecommendedCard() {
        return recommendedCard;
    }

    public void setRecommendedCard(CardEvaluationResult recommendedCard) {
        this.recommendedCard = recommendedCard;
    }

    public String getLlmReason() {
        return llmReason;
    }

    public void setLlmReason(String llmReason) {
        this.llmReason = llmReason;
    }

    public String getDecisionSource() {
        return decisionSource;
    }

    public void setDecisionSource(String decisionSource) {
        this.decisionSource = decisionSource;
    }

    public Double getFinalScore() {
        return finalScore;
    }

    public void setFinalScore(Double finalScore) {
        this.finalScore = finalScore;
    }
}