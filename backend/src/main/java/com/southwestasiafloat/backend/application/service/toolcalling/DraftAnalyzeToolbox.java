package com.southwestasiafloat.backend.application.service.toolcalling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.application.service.toolcalling.model.RuleRankingResult;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.CardEvaluationResult;
import com.southwestasiafloat.backend.domain.model.DeckState;
import com.southwestasiafloat.backend.domain.model.DraftHistoryEntry;
import com.southwestasiafloat.backend.domain.model.DraftSession;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DraftAnalyzeToolbox {

    private final DraftAnalyzeContextStore contextStore;
    private final ObjectMapper objectMapper;

    public DraftAnalyzeToolbox(DraftAnalyzeContextStore contextStore, ObjectMapper objectMapper) {
        this.contextStore = contextStore;
        this.objectMapper = objectMapper;
    }

    @Tool("Load the current draft session snapshot, including current pick number, picked cards, and deck state.")
    public String getSessionSnapshot(@P("The active draft session id") String sessionId) {
        DraftSession session = contextStore.getSession(sessionId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.getSessionId());
        payload.put("currentPickNo", session.getCurrentPickNo());
        payload.put("pickedCards", session.getPickedCards().stream()
                .map(this::cardSummary)
                .collect(Collectors.toList()));
        payload.put("deckState", session.getDeckState() != null ? session.getDeckState() : contextStore.getDeckState(sessionId));
        return toJson(payload);
    }

    @Tool("Extract the current candidate cards from the uploaded screenshot for this analysis request.")
    public String extractCandidates(@P("The current analysis request id") String analysisId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("analysisId", analysisId);
        payload.put("candidates", contextStore.getCandidates(analysisId).stream()
                .map(this::cardSummary)
                .collect(Collectors.toList()));
        return toJson(payload);
    }

    @Tool("Evaluate the arena base scores for the candidates in the current analysis request.")
    public String evaluateBaseScores(@P("The current analysis request id") String analysisId) {
        List<CardEvaluationResult> evaluations = contextStore.getEvaluations(analysisId);
        List<Map<String, Object>> payload = evaluations.stream()
                .map(evaluation -> {
                    Map<String, Object> candidate = new LinkedHashMap<>();
                    candidate.put("cardName", evaluation.getCard() != null ? evaluation.getCard().getName() : null);
                    candidate.put("baseScore", evaluation.getBaseScore());
                    candidate.put("adjustedScore", evaluation.getAdjustedScore());
                    candidate.put("count", evaluation.getCount());
                    candidate.put("source", evaluation.getSource());
                    candidate.put("matched", evaluation.isMatched());
                    candidate.put("comment", evaluation.getComment());
                    return candidate;
                })
                .collect(Collectors.toList());
        return toJson(payload);
    }

    @Tool("Analyze the current deck state for the active draft session.")
    public String analyzeDeckState(@P("The active draft session id") String sessionId) {
        DeckState deckState = contextStore.getDeckState(sessionId);
        return toJson(deckState);
    }

    @Tool("Get recent draft history for the active session, including recommended and actual picks.")
    public String getPickHistory(@P("The active draft session id") String sessionId) {
        List<DraftHistoryEntry> history = contextStore.getRecentHistory(sessionId);
        List<Map<String, Object>> payload = history.stream()
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("pickNo", entry.getPickNo());
                    item.put("analyzedAt", entry.getAnalyzedAt());
                    item.put("recommendedCard", entry.getRecommendedCard() != null ? entry.getRecommendedCard().getName() : null);
                    item.put("pickedCard", entry.getPickedCard() != null ? entry.getPickedCard().getName() : null);
                    item.put("status", entry.getStatus());
                    item.put("reason", entry.getReason());
                    item.put("finalScore", entry.getFinalScore());
                    return item;
                })
                .collect(Collectors.toList());
        return toJson(payload);
    }

    @Tool("Get the deterministic rule-based ranking for the candidates based on base score, deck state, and pick context.")
    public String rankCandidates(@P("The current analysis request id") String analysisId,
                                 @P("The active draft session id") String sessionId) {
        RuleRankingResult rankingResult = contextStore.getRuleRanking(analysisId, sessionId);
        return toJson(rankingResult);
    }

    private Map<String, Object> cardSummary(Card card) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", card.getName());
        summary.put("nation", card.getNation());
        summary.put("cost", card.getCost());
        summary.put("type", card.getType());
        summary.put("count", card.getCount());
        summary.put("description", card.getDescription());
        return summary;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize tool output", e);
        }
    }
}
