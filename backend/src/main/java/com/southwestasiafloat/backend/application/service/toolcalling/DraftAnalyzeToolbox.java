package com.southwestasiafloat.backend.application.service.toolcalling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.application.service.KnowledgeBaseService;
import com.southwestasiafloat.backend.application.service.toolcalling.model.RuleRankingResult;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.CardEvaluationResult;
import com.southwestasiafloat.backend.domain.model.DeckState;
import com.southwestasiafloat.backend.domain.model.DraftHistoryEntry;
import com.southwestasiafloat.backend.domain.model.DraftSession;
import com.southwestasiafloat.backend.domain.model.KnowledgeDocument;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DraftAnalyzeToolbox {

    private static final Map<String, String> MECHANIC_TAGS = Map.ofEntries(
            Map.entry("\u5b88\u62a4", "guard"),
            Map.entry("\u95ea\u51fb", "blitz"),
            Map.entry("\u594b\u6218", "double-attack"),
            Map.entry("\u4f0f\u51fb", "ambush"),
            Map.entry("\u91cd\u7532", "armor"),
            Map.entry("\u51b2\u51fb", "impact"),
            Map.entry("\u70df\u5e55", "smokescreen"),
            Map.entry("\u90e8\u7f72", "deployment"),
            Map.entry("\u4ea1\u8ba1", "death-trigger"),
            Map.entry("\u4fee\u590d", "repair"),
            Map.entry("\u5012\u8ba1\u65f6", "countdown"),
            Map.entry("\u514d\u75ab", "immune"),
            Map.entry("\u52a8\u5458", "mobilize"),
            Map.entry("\u538b\u5236", "suppression"),
            Map.entry("\u5c71\u5730", "mountain"),
            Map.entry("\u5f00\u53d1", "develop"),
            Map.entry("\u60c5\u62a5", "intel"),
            Map.entry("\u6291\u5236", "silence"),
            Map.entry("\u63a7\u5236", "control"),
            Map.entry("\u64a4\u9000", "retreat"),
            Map.entry("\u6d41\u4ea1", "exile"),
            Map.entry("\u8001\u5175", "veteran"),
            Map.entry("\u6536\u7f34", "confiscate"),
            Map.entry("\u94b3\u51fb", "pincer"),
            Map.entry("\u9690\u853d", "concealed"),
            Map.entry("\u8f6c\u6362", "transform"),
            Map.entry("\u534f\u529b", "cooperation")
    );

    private final DraftAnalyzeContextStore contextStore;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper objectMapper;

    public DraftAnalyzeToolbox(DraftAnalyzeContextStore contextStore,
                               KnowledgeBaseService knowledgeBaseService,
                               ObjectMapper objectMapper) {
        this.contextStore = contextStore;
        this.knowledgeBaseService = knowledgeBaseService;
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

    @Tool("Retrieve relevant draft strategy and mechanic knowledge from the knowledge base for the current analysis context.")
    public String retrieveStrategyKnowledge(@P("The current analysis request id") String analysisId,
                                            @P("The active draft session id") String sessionId) {
        DraftSession session = contextStore.getSession(sessionId);
        List<Card> candidates = contextStore.getCandidates(analysisId);
        DeckState deckState = contextStore.getDeckState(sessionId);

        List<String> tags = buildKnowledgeTags(session, candidates, deckState);
        List<String> keywords = buildKnowledgeKeywords(candidates);
        List<KnowledgeDocument> documents = knowledgeBaseService.retrieveRelevant(tags, keywords, 6);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appliedTags", tags);
        payload.put("keywords", keywords);
        payload.put("documents", documents.stream()
                .map(this::knowledgeSummary)
                .collect(Collectors.toList()));
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

    private Map<String, Object> knowledgeSummary(KnowledgeDocument document) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", document.getId());
        summary.put("collection", document.getCollection());
        summary.put("title", document.getTitle());
        summary.put("type", document.getType());
        summary.put("summary", document.getSummary());
        summary.put("content", truncate(document.getContent(), 260));
        summary.put("tags", document.getTags());
        summary.put("stability", document.getStability());
        summary.put("sourceTitle", document.getSourceTitle());
        return summary;
    }

    private List<String> buildKnowledgeTags(DraftSession session, List<Card> candidates, DeckState deckState) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add("general");
        tags.add("draft");
        tags.add("curve");

        int currentPickNo = session.getCurrentPickNo() != null ? session.getCurrentPickNo() : 1;
        if (currentPickNo <= 20) {
            tags.add("skeleton");
            tags.add("unit-quality");
        } else {
            tags.add("draft-adjustment");
            tags.add("decision-shift");
        }

        if (deckState != null) {
            if (deckState.getTags() != null) {
                deckState.getTags().stream()
                        .filter(Objects::nonNull)
                        .map(this::normalizeToken)
                        .filter(value -> !value.isBlank())
                        .forEach(tags::add);
            }

            if (needsLowCostSupport(session, deckState)) {
                tags.add("low-cost");
                tags.add("curve-fix");
                tags.add("structure-correction");
            }

            if (deckState.getOrderCount() > deckState.getUnitCount()) {
                tags.add("ratio");
                tags.add("deckbuilding");
            }
        }

        candidates.stream()
                .map(Card::getNation)
                .map(this::nationTag)
                .filter(value -> !value.isBlank())
                .forEach(tags::add);

        tags.addAll(extractMechanicSignals(candidates));
        return new ArrayList<>(tags);
    }

    private List<String> buildKnowledgeKeywords(List<Card> candidates) {
        Set<String> keywords = new LinkedHashSet<>();

        candidates.stream()
                .map(Card::getNation)
                .map(this::nationKeyword)
                .filter(value -> !value.isBlank())
                .forEach(keywords::add);

        candidates.stream()
                .map(Card::getDescription)
                .filter(Objects::nonNull)
                .forEach(description -> keywords.addAll(matchMechanicKeywords(description)));

        candidates.stream()
                .map(Card::getKeywords)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(keyword -> {
                    keywords.add(keyword);
                    keywords.addAll(matchMechanicKeywords(keyword));
                });

        return keywords.stream()
                .sorted(Comparator.naturalOrder())
                .limit(10)
                .collect(Collectors.toList());
    }

    private Set<String> extractMechanicSignals(List<Card> candidates) {
        Set<String> signals = new LinkedHashSet<>();
        for (Card card : candidates) {
            if (card == null) {
                continue;
            }

            if ("order".equalsIgnoreCase(card.getType())) {
                signals.add("removal");
            }

            signals.addAll(matchMechanicTags(card.getDescription()));

            if (card.getKeywords() != null) {
                for (String keyword : card.getKeywords()) {
                    signals.addAll(matchMechanicTags(keyword));
                }
            }
        }
        return signals;
    }

    private Set<String> matchMechanicTags(String text) {
        Set<String> matches = new LinkedHashSet<>();
        String normalized = normalizeToken(text);
        for (Map.Entry<String, String> entry : MECHANIC_TAGS.entrySet()) {
            if (normalized.contains(normalizeToken(entry.getKey()))) {
                matches.add(entry.getValue());
            }
        }
        return matches;
    }

    private List<String> matchMechanicKeywords(String text) {
        String normalized = normalizeToken(text);
        return MECHANIC_TAGS.keySet().stream()
                .filter(keyword -> normalized.contains(normalizeToken(keyword)))
                .sorted()
                .collect(Collectors.toList());
    }

    private boolean needsLowCostSupport(DraftSession session, DeckState deckState) {
        int currentPickNo = session.getCurrentPickNo() != null ? session.getCurrentPickNo() : 1;
        int earlyCount = deckState.getEarlyCount();

        if (currentPickNo <= 8) {
            return earlyCount <= 1;
        }
        if (currentPickNo <= 20) {
            return earlyCount < Math.max(3, currentPickNo / 5);
        }
        return earlyCount < Math.max(5, currentPickNo / 4);
    }

    private String nationTag(String nation) {
        String normalized = normalizeToken(nation);
        if (normalized.contains("japan") || normalized.contains("\u65e5\u672c")) {
            return "japan";
        }
        if (normalized.contains("germany") || normalized.contains("\u5fb7\u56fd")) {
            return "germany";
        }
        if (normalized.contains("usa") || normalized.contains("united states") || normalized.contains("\u7f8e\u56fd")) {
            return "usa";
        }
        if (normalized.contains("uk") || normalized.contains("brit") || normalized.contains("england") || normalized.contains("\u82f1\u56fd")) {
            return "uk";
        }
        if (normalized.contains("ussr") || normalized.contains("soviet") || normalized.contains("\u82cf\u8054")) {
            return "ussr";
        }
        return "";
    }

    private String nationKeyword(String nation) {
        String tag = nationTag(nation);
        return switch (tag) {
            case "japan" -> "\u65e5\u672c";
            case "germany" -> "\u5fb7\u56fd";
            case "usa" -> "\u7f8e\u56fd";
            case "uk" -> "\u82f1\u56fd";
            case "ussr" -> "\u82cf\u8054";
            default -> "";
        };
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize tool output", e);
        }
    }
}
