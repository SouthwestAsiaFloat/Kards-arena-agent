package com.southwestasiafloat.backend.application.service.toolcalling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.application.service.toolcalling.model.RuleRankingResult;
import com.southwestasiafloat.backend.application.service.toolcalling.model.ToolCallingDraftAnalysisPayload;
import com.southwestasiafloat.backend.application.service.toolcalling.model.ToolCallingDraftAnalysisResult;
import com.southwestasiafloat.backend.config.ArenaAnalysisProperties;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.CardEvaluationResult;
import com.southwestasiafloat.backend.domain.model.FinalDecision;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ToolCallingDraftAnalyzeService {

    private final DraftAnalyzeContextStore contextStore;
    private final ObjectMapper objectMapper;
    private final DraftAnalyzeAgent analyzeAgent;
    private final Semaphore llmSemaphore;
    private final Duration llmPermitTimeout;
    private final int circuitBreakerFailureThreshold;
    private final Duration circuitBreakerOpenDuration;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger consecutiveLlmFailures = new AtomicInteger();
    private volatile Instant circuitOpenUntil = Instant.EPOCH;

    public ToolCallingDraftAnalyzeService(OpenAiChatModel chatModel,
                                          DraftAnalyzeToolbox toolbox,
                                          DraftAnalyzeContextStore contextStore,
                                          ObjectMapper objectMapper,
                                          ArenaAnalysisProperties analysisProperties,
                                          MeterRegistry meterRegistry) {
        this.contextStore = contextStore;
        this.objectMapper = objectMapper;
        this.llmSemaphore = new Semaphore(Math.max(1, analysisProperties.getMaxConcurrentLlmCalls()));
        this.llmPermitTimeout = analysisProperties.getLlmPermitTimeout();
        this.circuitBreakerFailureThreshold = Math.max(1, analysisProperties.getLlmCircuitBreakerFailureThreshold());
        this.circuitBreakerOpenDuration = analysisProperties.getLlmCircuitBreakerOpenDuration();
        this.meterRegistry = meterRegistry;
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
        rejectIfCircuitOpen();
        boolean acquired = acquireLlmPermit();
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String agentResponse = analyzeAgent.analyze(analysisId, effectiveSessionId);
            ToolCallingDraftAnalysisPayload payload = parseAgentPayload(agentResponse);
            recordLlmSuccess();
            return buildResult(analysisId, effectiveSessionId, payload);
        } catch (RuntimeException ex) {
            recordLlmFailure();
            throw ex;
        } finally {
            if (acquired) {
                llmSemaphore.release();
            }
            sample.stop(meterRegistry.timer("arena.llm.tool_calling.duration"));
        }
    }

    private boolean acquireLlmPermit() {
        long timeoutMillis = llmPermitTimeout != null
                ? Math.max(0, llmPermitTimeout.toMillis())
                : 0;
        try {
            boolean acquired = llmSemaphore.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new IllegalStateException("LLM concurrency limit reached");
            }
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for LLM concurrency permit", ex);
        }
    }

    private void rejectIfCircuitOpen() {
        if (Instant.now().isBefore(circuitOpenUntil)) {
            meterRegistry.counter("arena.llm.circuit.open").increment();
            throw new IllegalStateException("LLM circuit breaker is open until " + circuitOpenUntil);
        }
    }

    private void recordLlmSuccess() {
        consecutiveLlmFailures.set(0);
        circuitOpenUntil = Instant.EPOCH;
        meterRegistry.counter("arena.llm.calls", "result", "success").increment();
    }

    private void recordLlmFailure() {
        int failures = consecutiveLlmFailures.incrementAndGet();
        meterRegistry.counter("arena.llm.calls", "result", "failure").increment();
        if (failures >= circuitBreakerFailureThreshold) {
            Duration openDuration = circuitBreakerOpenDuration != null
                    ? circuitBreakerOpenDuration
                    : Duration.ofSeconds(30);
            circuitOpenUntil = Instant.now().plus(openDuration);
            meterRegistry.counter("arena.llm.circuit.tripped").increment();
            log.warn("LLM circuit breaker opened for {} after {} consecutive failures",
                    openDuration, failures);
        }
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
        meterRegistry.counter("arena.analyze.fallbacks").increment();
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
