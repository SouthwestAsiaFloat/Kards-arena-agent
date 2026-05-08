package com.southwestasiafloat.backend.application.analysis.toolcalling;

/**
 * 工具调用分析服务，负责统一同步和 OCR 结果分析流程。
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.application.analysis.model.RuleRankingResult;
import com.southwestasiafloat.backend.application.analysis.model.ToolCallingDraftAnalysisPayload;
import com.southwestasiafloat.backend.application.analysis.model.ToolCallingDraftAnalysisResult;
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


/**
 * Tool Calling 分析服务。
 *
 * <p>职责：
 * <ul>
 *     <li>统一同步图片分析与异步 OCR 结果分析入口</li>
 *     <li>为 Agent 创建分析上下文（analysisId）</li>
 *     <li>调用 LangChain4j Tool Calling Agent</li>
 *     <li>控制 LLM 并发量（Semaphore）</li>
 *     <li>提供简单熔断保护，避免连续失败时持续调用 LLM</li>
 *     <li>解析 Agent 返回 JSON，并生成最终推荐结果</li>
 *     <li>当 Agent 失败时，自动 fallback 到规则排序结果</li>
 * </ul>
 *
 * <p>整体链路：
 * 图片 / OCR JSON
 * ↓
 * ContextStore 创建 analysisId
 * ↓
 * Agent Tool Calling
 * ↓
 * 校验返回结果
 * ↓
 * 构建 FinalDecision
 * ↓
 * 返回 ToolCallingDraftAnalysisResult
 */
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

    /**
     * 同步分析入口。
     *
     * <p>用于：
     * 前端直接上传截图 → OCR + Tool Calling + 推荐分析。
     *
     * <p>流程：
     * 创建分析上下文 → 调用 Agent → 构建结果 → 清理上下文。
     */
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

    /**
     * 异步 OCR 分析入口。
     *
     * <p>用于：
     * OCR Worker 已经完成识别，只需要继续执行 Tool Calling 分析。
     *
     * <p>流程：
     * OCR JSON → ContextStore → Agent → 推荐结果。
     */
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


    /**
     * 真正执行 Tool Calling Agent 的核心方法。
     *
     * <p>执行顺序：
     * <ol>
     *     <li>检查熔断器是否开启</li>
     *     <li>获取 LLM 并发许可</li>
     *     <li>调用 Agent</li>
     *     <li>解析并校验 Agent 返回结果</li>
     *     <li>构建最终推荐结果</li>
     *     <li>记录成功/失败指标</li>
     * </ol>
     */
    private ToolCallingDraftAnalysisResult analyzeContext(String analysisId, String effectiveSessionId) {
        rejectIfCircuitOpen();
        boolean acquired = acquireLlmPermit();
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String agentResponse = analyzeAgent.analyze(analysisId, effectiveSessionId);
            ToolCallingDraftAnalysisPayload payload = parseAgentPayload(agentResponse);
            validatePayload(payload);
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

    /**
     * 获取 LLM 并发许可。
     *
     * <p>通过 Semaphore 控制同时调用 LLM 的请求数，
     * 防止高并发下把模型服务打爆。
     */
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

    /**
     * 熔断检查。
     *
     * <p>如果近期 LLM 连续失败次数过多，
     * 则在熔断窗口内拒绝继续调用模型。
     */
    private void rejectIfCircuitOpen() {
        if (Instant.now().isBefore(circuitOpenUntil)) {
            meterRegistry.counter("arena.llm.circuit.open").increment();
            throw new IllegalStateException("LLM circuit breaker is open until " + circuitOpenUntil);
        }
    }

    /**
     * 记录一次成功调用。
     *
     * <p>成功时：
     * 清空连续失败计数，并关闭熔断状态。
     */
    private void recordLlmSuccess() {
        consecutiveLlmFailures.set(0);
        circuitOpenUntil = Instant.EPOCH;
        meterRegistry.counter("arena.llm.calls", "result", "success").increment();
    }

    /**
     * 记录一次失败调用。
     *
     * <p>当连续失败次数超过阈值时，
     * 会打开熔断器一段时间。
     */
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

    /**
     * 根据 Agent 返回 payload 构建最终推荐结果。
     *
     * <p>这里会校验：
     * Agent 推荐的卡牌必须存在于候选卡中。
     */
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

    /**
     * 构建 fallback 推荐结果。
     *
     * <p>触发场景：
     * Agent 超时、失败、熔断、返回非法候选卡。
     *
     * <p>逻辑：
     * 使用规则排序结果作为最终推荐。
     */
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

    /**
     * 校验 Agent 返回 payload。
     *
     * <p>确保推荐卡名称存在，避免空推荐进入后续流程。
     */
    private void validatePayload(ToolCallingDraftAnalysisPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Agent returned an empty payload");
        }
        if (payload.recommendedCardName() == null || payload.recommendedCardName().isBlank()) {
            throw new IllegalArgumentException("Agent returned an empty recommendedCardName");
        }
    }

    /**
     * 解析 Agent 返回结果。
     *
     * <p>优先直接解析 JSON；
     * 如果失败，则尝试从文本中提取 JSON 再解析。
     */
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

    /**
     * 从文本中提取 JSON 内容。
     *
     * <p>用于兼容模型返回：
     * “解释文字 + JSON” 的情况。
     */
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
