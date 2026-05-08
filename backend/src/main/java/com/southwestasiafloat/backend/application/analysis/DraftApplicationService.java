package com.southwestasiafloat.backend.application.analysis;

/**
 * 负责上传截图后的Agent分析编排。
 */

import com.southwestasiafloat.backend.application.analysis.model.ToolCallingDraftAnalysisResult;
import com.southwestasiafloat.backend.application.analysis.support.AnalysisRequestGuard;
import com.southwestasiafloat.backend.application.analysis.toolcalling.ToolCallingDraftAnalyzeService;
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

        // 草稿会话是可变的：分析截图时也会追加一条待确认的历史记录。
        // 同一 session 的操作必须串行，避免重复上传时状态写入交叉。
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

    // 防止两个请求同时截图并分析同一张牌，导致重复的计算和历史记录。通过分析锁串行化同一张截图的分析请求，提升效率和数据一致性。
    private DraftAnalyzeResponse analyzeWithDedup(String cacheKey, byte[] imageBytes, String sessionId) {
        return analyzeRequestLockManager.withAnalyzeLock(cacheKey, () -> {
            // 获取分析锁后再次检查，防止其他请求已经完成了同一张截图的分析。
            Optional<DraftAnalyzeResponse> cached = findCachedAnalyzeResult(cacheKey);
            if (cached.isPresent()) {
                log.info("Reused cached analyze result after waiting for key={}", cacheKey);
                return cached.get();
            }

            ToolCallingDraftAnalysisResult analysisResult =
                    toolCallingDraftAnalyzeService.analyze(imageBytes, sessionId);
            return buildResponseAndPersist(sessionId, cacheKey, analysisResult);
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



    // 真正的Agent分析链路
    private DraftAnalyzeResponse analyzeOcrResultUncached(String ocrRawJson, String sessionId, String cacheKey) {
        ToolCallingDraftAnalysisResult analysisResult =
                // 这里要调用tool-calling的分析接口，直接传OCR结果，让它走后续的Agent链路（包括解析OCR、构建环境信息、调用LLM决策等），
                // 而不是在这里先解析OCR再调用分析接口。这样可以保证分析链路的一致性和完整性，避免在这里解析OCR时出现和tool-calling不一致的情况。
                toolCallingDraftAnalyzeService.analyzeOcrResult(ocrRawJson, sessionId);
        return buildResponseAndPersist(sessionId, cacheKey, analysisResult);
    }

    // 分析完后的收尾
    private DraftAnalyzeResponse buildResponseAndPersist(String sessionId,
                                                         String cacheKey,
                                                         ToolCallingDraftAnalysisResult analysisResult) {
        DraftAnalyzeResponse response =
                new DraftAnalyzeResponse(analysisResult.offeredCards(), analysisResult.decision());

        saveAnalyzeHistory(sessionId, analysisResult.offeredCards(), analysisResult.decision());
        if (cacheKey != null && !cacheKey.isBlank()) {
            analyzeResultCache.put(cacheKey, response);
        }
        return response;
    }


    // 缓存Key构造器
    public String buildAnalyzeCacheKey(String sessionId, byte[] imageBytes) {
        String sessionKey = normalizeSessionKey(sessionId);
        int pickNo = resolveCurrentPickNo(sessionId);
        return sessionKey + ":" + pickNo + ":" + sha256Hex(imageBytes);
    }


    // 标准化可要， 如果key为空或全是空白字符，就用一个固定值代替，表示这是一个没有sessionId的分析请求。这样可以避免后续处理时频繁检查null或空字符串。
    private String normalizeSessionKey(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "analysis-only";
        }
        return sessionId;
    }

   // 获得当前的抓数
    private int resolveCurrentPickNo(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }

        return repository.findById(sessionId)
                .map(DraftSession::getCurrentPickNo)
                .orElse(0);
    }


   // 构造图片指纹
    private String sha256Hex(byte[] imageBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(imageBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }


    // 真正写入历史记录的方法
    private void saveAnalyzeHistory(String sessionId,
                                    List<Card> cards,
                                    FinalDecision decision) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        // 历史记录先以“待确认”写入，后续由 /pick 接口把最新一条标记为已确认。
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
