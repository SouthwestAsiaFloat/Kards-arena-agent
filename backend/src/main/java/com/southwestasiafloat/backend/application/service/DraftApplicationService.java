package com.southwestasiafloat.backend.application.service;

import com.southwestasiafloat.backend.application.service.toolcalling.ToolCallingDraftAnalyzeService;
import com.southwestasiafloat.backend.application.service.toolcalling.model.ToolCallingDraftAnalysisResult;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.DraftHistoryEntry;
import com.southwestasiafloat.backend.domain.model.DraftSession;
import com.southwestasiafloat.backend.domain.model.FinalDecision;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import com.southwestasiafloat.backend.infrastructure.repository.InMemorySessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DraftApplicationService {

    private static final DateTimeFormatter HISTORY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ToolCallingDraftAnalyzeService toolCallingDraftAnalyzeService;
    private final InMemorySessionRepository repository;

    public DraftApplicationService(ToolCallingDraftAnalyzeService toolCallingDraftAnalyzeService,
                                   InMemorySessionRepository repository) {
        this.toolCallingDraftAnalyzeService = toolCallingDraftAnalyzeService;
        this.repository = repository;
    }

    public DraftAnalyzeResponse analyze(MultipartFile file, String sessionId) throws Exception {
        ToolCallingDraftAnalysisResult analysisResult =
                toolCallingDraftAnalyzeService.analyze(file.getBytes(), sessionId);

        saveAnalyzeHistory(sessionId, analysisResult.offeredCards(), analysisResult.decision());
        return new DraftAnalyzeResponse(analysisResult.offeredCards(), analysisResult.decision());
    }

    private void saveAnalyzeHistory(String sessionId,
                                    List<Card> cards,
                                    FinalDecision decision) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

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
        historyEntry.setStatus("待确认");

        session.addHistoryEntry(historyEntry);
        repository.save(session);
    }

    private Card extractRecommendedCard(FinalDecision decision) {
        if (decision == null || decision.getRecommendedCard() == null) {
            return null;
        }
        return decision.getRecommendedCard().getCard();
    }

    private String resolveReason(FinalDecision decision) {
        if (decision == null) {
            return "后端未返回推荐理由";
        }

        if (decision.getLlmReason() != null && !decision.getLlmReason().isBlank()) {
            return decision.getLlmReason();
        }

        if (decision.getRecommendedCard() != null
                && decision.getRecommendedCard().getComment() != null
                && !decision.getRecommendedCard().getComment().isBlank()) {
            return decision.getRecommendedCard().getComment();
        }

        return "后端未返回推荐理由";
    }
}
