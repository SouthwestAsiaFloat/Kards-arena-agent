package com.southwestasiafloat.backend.application.service.toolcalling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.application.service.toolcalling.model.RuleRankingResult;
import com.southwestasiafloat.backend.application.service.toolcalling.model.ToolCallingDraftAnalysisPayload;
import com.southwestasiafloat.backend.application.service.toolcalling.model.ToolCallingDraftAnalysisResult;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.CardEvaluationResult;
import com.southwestasiafloat.backend.domain.model.FinalDecision;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class ToolCallingDraftAnalyzeService {

    private final DraftAnalyzeContextStore contextStore;
    private final ObjectMapper objectMapper;
    private final DraftAnalyzeAgent analyzeAgent;

    public ToolCallingDraftAnalyzeService(OpenAiChatModel chatModel,
                                          DraftAnalyzeToolbox toolbox,
                                          DraftAnalyzeContextStore contextStore,
                                          ObjectMapper objectMapper) {
        this.contextStore = contextStore;
        this.objectMapper = objectMapper;
        this.analyzeAgent = AiServices.builder(DraftAnalyzeAgent.class)
                .chatModel(chatModel)
                .tools(toolbox)
                .maxSequentialToolsInvocations(12)
                .build();
    }

    public ToolCallingDraftAnalysisResult analyze(byte[] imageBytes, String sessionId) {
        String effectiveSessionId = normalizeSessionId(sessionId);
        String analysisId = contextStore.createContext(imageBytes);

        try {
            return analyzeContext(analysisId, effectiveSessionId);
        } catch (Exception ex) {
            log.warn("Tool-calling analyze failed, falling back to rule-based ranking", ex);
            return buildFallbackResult(analysisId, effectiveSessionId,
                    "Tool calling failed, fallback to rule-based ranking: " + ex.getMessage());
        } finally {
            contextStore.clearContext(analysisId);
        }
    }

    public ToolCallingDraftAnalysisResult analyzeOcrResult(String ocrRawJson, String sessionId) {
        String effectiveSessionId = normalizeSessionId(sessionId);
        String analysisId = contextStore.createContextFromOcrResult(ocrRawJson);

        try {
            return analyzeContext(analysisId, effectiveSessionId);
        } catch (Exception ex) {
            log.warn("Tool-calling analyze failed after async OCR, falling back to rule-based ranking", ex);
            return buildFallbackResult(analysisId, effectiveSessionId,
                    "Tool calling failed after async OCR, fallback to rule-based ranking: " + ex.getMessage());
        } finally {
            contextStore.clearContext(analysisId);
        }
    }

    private ToolCallingDraftAnalysisResult analyzeContext(String analysisId, String effectiveSessionId) {
        String agentResponse = analyzeAgent.analyze(analysisId, effectiveSessionId);
        ToolCallingDraftAnalysisPayload payload = parseAgentPayload(agentResponse);
        return buildResult(analysisId, effectiveSessionId, payload);
    }

    private ToolCallingDraftAnalysisResult buildResult(String analysisId,
                                                       String sessionId,
                                                       ToolCallingDraftAnalysisPayload payload) {
        List<Card> offeredCards = contextStore.getCandidates(analysisId);
        CardEvaluationResult recommendedEvaluation = contextStore.findEvaluation(analysisId, payload.recommendedCardName());

        if (recommendedEvaluation == null) {
            return buildFallbackResult(analysisId, sessionId,
                    "Agent returned an invalid candidate name, fallback to rule-based ranking");
        }

        Double finalScore = contextStore.findRuleScore(analysisId, sessionId, recommendedEvaluation.getCard().getName());
        if (payload.finalScore() != null) {
            finalScore = payload.finalScore();
        }

        FinalDecision finalDecision = new FinalDecision(
                recommendedEvaluation,
                safeReason(payload.reason()),
                safeDecisionSource(payload.decisionSource()),
                finalScore != null ? finalScore : 0.0
        );

        return new ToolCallingDraftAnalysisResult(offeredCards, finalDecision);
    }

    private ToolCallingDraftAnalysisResult buildFallbackResult(String analysisId,
                                                               String sessionId,
                                                               String fallbackReason) {
        List<Card> offeredCards = contextStore.getCandidates(analysisId);
        RuleRankingResult rankingResult = contextStore.getRuleRanking(analysisId, sessionId);
        CardEvaluationResult recommendedEvaluation = contextStore.findEvaluation(analysisId, rankingResult.topCardName());

        String reason = fallbackReason;
        if (!rankingResult.candidates().isEmpty()) {
            reason = "Rule-based ranking recommends " + rankingResult.topCardName()
                    + " with score=" + formatScore(rankingResult.topFinalScore())
                    + ". " + fallbackReason;
        }

        FinalDecision finalDecision = new FinalDecision(
                recommendedEvaluation,
                reason,
                "rule-fallback",
                rankingResult.topFinalScore() != null ? rankingResult.topFinalScore() : 0.0
        );

        return new ToolCallingDraftAnalysisResult(offeredCards, finalDecision);
    }

    private ToolCallingDraftAnalysisPayload parseAgentPayload(String agentResponse) {
        try {
            return objectMapper.readValue(agentResponse, ToolCallingDraftAnalysisPayload.class);
        } catch (Exception directParseFailure) {
            String jsonOnly = extractJson(agentResponse);
            if (jsonOnly != null) {
                try {
                    return objectMapper.readValue(jsonOnly, ToolCallingDraftAnalysisPayload.class);
                } catch (Exception nestedFailure) {
                    directParseFailure.addSuppressed(nestedFailure);
                }
            }
            throw new IllegalArgumentException("Unable to parse agent response: " + agentResponse, directParseFailure);
        }
    }

    private String extractJson(String text) {
        if (text == null) {
            return null;
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "analysis-only";
        }
        return sessionId;
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Tool calling completed without a detailed explanation.";
        }
        return reason;
    }

    private String safeDecisionSource(String decisionSource) {
        if (decisionSource == null || decisionSource.isBlank()) {
            return "tool-calling-rag";
        }
        return decisionSource;
    }

    private String formatScore(Double score) {
        if (score == null) {
            return "0.00";
        }
        return String.format(Locale.US, "%.2f", score);
    }
}
