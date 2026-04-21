package com.southwestasiafloat.backend.application.service.toolcalling;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.application.service.toolcalling.model.RuleRankingResult;
import com.southwestasiafloat.backend.domain.gateway.OcrGateway;
import com.southwestasiafloat.backend.domain.gateway.SessionRepository;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.CardEvaluationResult;
import com.southwestasiafloat.backend.domain.model.DeckState;
import com.southwestasiafloat.backend.domain.model.DraftHistoryEntry;
import com.southwestasiafloat.backend.domain.model.DraftSession;
import com.southwestasiafloat.backend.domain.model.OfferedCards;
import com.southwestasiafloat.backend.domain.service.CardEvaluationService;
import com.southwestasiafloat.backend.domain.service.DeckStateAnalyzer;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DraftAnalyzeContextStore {

    private final Map<String, AnalysisContext> contexts = new ConcurrentHashMap<>();

    private final OcrGateway ocrGateway;
    private final ObjectMapper objectMapper;
    private final CardEvaluationService cardEvaluationService;
    private final RuleBasedDraftRankingService ruleBasedDraftRankingService;
    private final SessionRepository sessionRepository;
    private final DeckStateAnalyzer deckStateAnalyzer;

    public DraftAnalyzeContextStore(OcrGateway ocrGateway,
                                    ObjectMapper objectMapper,
                                    CardEvaluationService cardEvaluationService,
                                    RuleBasedDraftRankingService ruleBasedDraftRankingService,
                                    SessionRepository sessionRepository,
                                    DeckStateAnalyzer deckStateAnalyzer) {
        this.ocrGateway = ocrGateway;
        this.objectMapper = objectMapper;
        this.cardEvaluationService = cardEvaluationService;
        this.ruleBasedDraftRankingService = ruleBasedDraftRankingService;
        this.sessionRepository = sessionRepository;
        this.deckStateAnalyzer = deckStateAnalyzer;
    }

    public String createContext(byte[] imageBytes) {
        String analysisId = UUID.randomUUID().toString();
        contexts.put(analysisId, new AnalysisContext(imageBytes));
        return analysisId;
    }

    public String createContextFromOcrResult(String ocrRawJson) {
        String analysisId = UUID.randomUUID().toString();
        AnalysisContext context = new AnalysisContext(null);
        context.ocrRawJson = ocrRawJson;
        context.candidates = parseCards(ocrRawJson);
        contexts.put(analysisId, context);
        return analysisId;
    }

    public void clearContext(String analysisId) {
        contexts.remove(analysisId);
    }

    public List<Card> getCandidates(String analysisId) {
        AnalysisContext context = getRequiredContext(analysisId);
        if (context.candidates == null) {
            synchronized (context) {
                if (context.candidates == null) {
                    if (context.imageBytes == null) {
                        context.candidates = parseCards(context.ocrRawJson);
                    } else {
                        context.ocrRawJson = ocrGateway.analyzeImage(context.imageBytes);
                        context.candidates = parseCards(context.ocrRawJson);
                    }
                }
            }
        }
        return context.candidates;
    }

    public List<CardEvaluationResult> getEvaluations(String analysisId) {
        AnalysisContext context = getRequiredContext(analysisId);
        if (context.evaluations == null) {
            synchronized (context) {
                if (context.evaluations == null) {
                    OfferedCards offeredCards = toOfferedCards(getCandidates(analysisId));
                    context.evaluations = cardEvaluationService.evaluate(offeredCards);
                }
            }
        }
        return context.evaluations;
    }

    public RuleRankingResult getRuleRanking(String analysisId, String sessionId) {
        DraftSession session = getSession(sessionId);
        return ruleBasedDraftRankingService.rank(getEvaluations(analysisId), session);
    }

    public DraftSession getSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            DraftSession currentSession = sessionRepository.get();
            return currentSession != null ? currentSession : new DraftSession("analysis-only");
        }

        Optional<DraftSession> storedSession = sessionRepository.findById(sessionId);
        return storedSession.orElseGet(() -> new DraftSession(sessionId));
    }

    public DeckState getDeckState(String sessionId) {
        DraftSession session = getSession(sessionId);
        if (session.getDeckState() != null) {
            return session.getDeckState();
        }
        return deckStateAnalyzer.analyze(session.getPickedCards());
    }

    public List<DraftHistoryEntry> getRecentHistory(String sessionId) {
        DraftSession session = getSession(sessionId);
        if (session.getHistory() == null || session.getHistory().isEmpty()) {
            return List.of();
        }

        int endIndex = Math.min(5, session.getHistory().size());
        return session.getHistory().subList(0, endIndex);
    }

    public CardEvaluationResult findEvaluation(String analysisId, String cardName) {
        if (cardName == null || cardName.isBlank()) {
            return null;
        }

        return getEvaluations(analysisId).stream()
                .filter(evaluation -> evaluation.getCard() != null)
                .filter(evaluation -> sameCardName(evaluation.getCard().getName(), cardName))
                .findFirst()
                .orElse(null);
    }

    public Double findRuleScore(String analysisId, String sessionId, String cardName) {
        RuleRankingResult rankingResult = getRuleRanking(analysisId, sessionId);
        return rankingResult.candidates().stream()
                .filter(candidate -> sameCardName(candidate.cardName(), cardName))
                .map(candidate -> candidate.finalScore())
                .findFirst()
                .orElse(null);
    }

    public String getOcrRawJson(String analysisId) {
        AnalysisContext context = getRequiredContext(analysisId);
        if (context.ocrRawJson == null) {
            getCandidates(analysisId);
        }
        return context.ocrRawJson;
    }

    private AnalysisContext getRequiredContext(String analysisId) {
        AnalysisContext context = contexts.get(analysisId);
        if (context == null) {
            throw new IllegalArgumentException("Unknown analysisId: " + analysisId);
        }
        return context;
    }

    private List<Card> parseCards(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, new TypeReference<List<Card>>() {});
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private OfferedCards toOfferedCards(List<Card> cards) {
        OfferedCards offeredCards = new OfferedCards();
        if (cards.size() > 0) {
            offeredCards.setCard1(cards.get(0));
        }
        if (cards.size() > 1) {
            offeredCards.setCard2(cards.get(1));
        }
        if (cards.size() > 2) {
            offeredCards.setCard3(cards.get(2));
        }
        return offeredCards;
    }

    private boolean sameCardName(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", "");
    }

    private static final class AnalysisContext {
        private final byte[] imageBytes;
        private volatile String ocrRawJson;
        private volatile List<Card> candidates;
        private volatile List<CardEvaluationResult> evaluations;

        private AnalysisContext(byte[] imageBytes) {
            this.imageBytes = imageBytes;
        }
    }
}
