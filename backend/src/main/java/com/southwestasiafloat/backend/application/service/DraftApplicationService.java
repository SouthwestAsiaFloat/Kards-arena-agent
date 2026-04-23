package com.southwestasiafloat.backend.application.service;

import com.southwestasiafloat.backend.application.service.toolcalling.ToolCallingDraftAnalyzeService;
import com.southwestasiafloat.backend.application.service.toolcalling.model.ToolCallingDraftAnalysisResult;
import com.southwestasiafloat.backend.domain.gateway.AnalyzeRequestLockManager;
import com.southwestasiafloat.backend.domain.gateway.AnalyzeResultCache;
import com.southwestasiafloat.backend.domain.gateway.SessionLockManager;
import com.southwestasiafloat.backend.domain.gateway.SessionRepository;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.DraftHistoryEntry;
import com.southwestasiafloat.backend.domain.model.DraftSession;
import com.southwestasiafloat.backend.domain.model.FinalDecision;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DraftApplicationService {

    private static final DateTimeFormatter HISTORY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ToolCallingDraftAnalyzeService toolCallingDraftAnalyzeService;
    private final SessionRepository repository;
    private final SessionLockManager sessionLockManager;
    private final AnalyzeResultCache analyzeResultCache;
    private final AnalyzeRequestLockManager analyzeRequestLockManager;
    private final AnalysisRequestGuard analysisRequestGuard;

    public DraftApplicationService(ToolCallingDraftAnalyzeService toolCallingDraftAnalyzeService,
                                   SessionRepository repository,
                                   SessionLockManager sessionLockManager,
                                   AnalyzeResultCache analyzeResultCache,
                                   AnalyzeRequestLockManager analyzeRequestLockManager,
                                   AnalysisRequestGuard analysisRequestGuard) {
        this.toolCallingDraftAnalyzeService = toolCallingDraftAnalyzeService;
        this.repository = repository;
        this.sessionLockManager = sessionLockManager;
        this.analyzeResultCache = analyzeResultCache;
        this.analyzeRequestLockManager = analyzeRequestLockManager;
        this.analysisRequestGuard = analysisRequestGuard;
    }

    public DraftAnalyzeResponse analyze(MultipartFile file, String sessionId) throws Exception {
        analysisRequestGuard.validateUpload(file);
        byte[] imageBytes = file.getBytes();
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionLockManager.withSessionLock(sessionId, () -> analyzeLocked(imageBytes, sessionId));
        }
        return analyzeLocked(imageBytes, sessionId);
    }

    private DraftAnalyzeResponse analyzeLocked(byte[] imageBytes, String sessionId) {
        String cacheKey = buildAnalyzeCacheKey(sessionId, imageBytes);

        Optional<DraftAnalyzeResponse> cached = findCachedAnalyzeResult(cacheKey);
        if (cached.isPresent()) {
            log.info("Reused cached analyze result for key={}", cacheKey);
            return cached.get();
        }

        return analyzeWithDedup(cacheKey, imageBytes, sessionId);
    }

    private DraftAnalyzeResponse analyzeWithDedup(String cacheKey, byte[] imageBytes, String sessionId) {
        return analyzeRequestLockManager.withAnalyzeLock(cacheKey, () -> {
            Optional<DraftAnalyzeResponse> cached = findCachedAnalyzeResult(cacheKey);
            if (cached.isPresent()) {
                log.info("Reused cached analyze result after waiting for key={}", cacheKey);
                return cached.get();
            }

            ToolCallingDraftAnalysisResult analysisResult =
                    toolCallingDraftAnalyzeService.analyze(imageBytes, sessionId);

            DraftAnalyzeResponse response =
                    new DraftAnalyzeResponse(analysisResult.offeredCards(), analysisResult.decision());

            saveAnalyzeHistory(sessionId, analysisResult.offeredCards(), analysisResult.decision());
            analyzeResultCache.put(cacheKey, response);
            return response;
        });
    }

    public Optional<DraftAnalyzeResponse> findCachedAnalyzeResult(String cacheKey) {
        return analyzeResultCache.get(cacheKey);
    }

    public DraftAnalyzeResponse analyzeOcrResult(String ocrRawJson, String sessionId, String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return analyzeOcrResultUncached(ocrRawJson, sessionId, null);
        }

        return analyzeRequestLockManager.withAnalyzeLock(cacheKey, () -> {
            Optional<DraftAnalyzeResponse> cached = findCachedAnalyzeResult(cacheKey);
            if (cached.isPresent()) {
                log.info("Reused cached async analyze result for key={}", cacheKey);
                return cached.get();
            }

            return analyzeOcrResultUncached(ocrRawJson, sessionId, cacheKey);
        });
    }

    private DraftAnalyzeResponse analyzeOcrResultUncached(String ocrRawJson, String sessionId, String cacheKey) {
        ToolCallingDraftAnalysisResult analysisResult =
                toolCallingDraftAnalyzeService.analyzeOcrResult(ocrRawJson, sessionId);

        DraftAnalyzeResponse response =
                new DraftAnalyzeResponse(analysisResult.offeredCards(), analysisResult.decision());

        saveAnalyzeHistory(sessionId, analysisResult.offeredCards(), analysisResult.decision());
        if (cacheKey != null && !cacheKey.isBlank()) {
            analyzeResultCache.put(cacheKey, response);
        }
        return response;
    }

    public String buildAnalyzeCacheKey(String sessionId, byte[] imageBytes) {
        String sessionKey = normalizeSessionKey(sessionId);
        int pickNo = resolveCurrentPickNo(sessionId);
        return sessionKey + ":" + pickNo + ":" + sha256Hex(imageBytes);
    }

    private String normalizeSessionKey(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "analysis-only";
        }
        return sessionId;
    }

    private int resolveCurrentPickNo(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }

        return repository.findById(sessionId)
                .map(DraftSession::getCurrentPickNo)
                .orElse(0);
    }

    private String sha256Hex(byte[] imageBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(imageBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void saveAnalyzeHistory(String sessionId,
                                    List<Card> cards,
                                    FinalDecision decision) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        sessionLockManager.runWithSessionLock(sessionId, () -> {
            Optional<DraftSession> sessionOptional = repository.findById(sessionId);
            if (sessionOptional.isEmpty()) {
                log.warn("Skip history persistence because session {} does not exist", sessionId);
                return;
            }

            DraftSession session = sessionOptional.get();
            DraftHistoryEntry historyEntry = new DraftHistoryEntry();
            historyEntry.setPickNo(session.getCurrentPickNo());
            historyEntry.setAnalyzedAt(LocalDateTime.now().format(HISTORY_TIME_FORMATTER));
            historyEntry.setOfferedCards(cards);
            historyEntry.setRecommendedCard(extractRecommendedCard(decision));
            historyEntry.setDecisionSource(decision != null ? decision.getDecisionSource() : null);
            historyEntry.setFinalScore(decision != null ? decision.getFinalScore() : null);
            historyEntry.setReason(resolveReason(decision));
            historyEntry.setStatus("PENDING_CONFIRMATION");

            session.addHistoryEntry(historyEntry);
            repository.save(session);
        });
    }

    private Card extractRecommendedCard(FinalDecision decision) {
        if (decision == null || decision.getRecommendedCard() == null) {
            return null;
        }
        return decision.getRecommendedCard().getCard();
    }

    private String resolveReason(FinalDecision decision) {
        if (decision == null) {
            return "Backend did not return a recommendation reason.";
        }

        if (decision.getLlmReason() != null && !decision.getLlmReason().isBlank()) {
            return decision.getLlmReason();
        }

        if (decision.getRecommendedCard() != null
                && decision.getRecommendedCard().getComment() != null
                && !decision.getRecommendedCard().getComment().isBlank()) {
            return decision.getRecommendedCard().getComment();
        }

        return "Backend did not return a recommendation reason.";
    }
}
