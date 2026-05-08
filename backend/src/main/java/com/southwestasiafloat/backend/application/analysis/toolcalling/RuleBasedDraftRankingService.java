package com.southwestasiafloat.backend.application.analysis.toolcalling;

/**
 * 规则排序服务，负责基于本地规则生成候选排名。
 */

import com.southwestasiafloat.backend.application.analysis.model.RuleRankedCandidate;
import com.southwestasiafloat.backend.application.analysis.model.RuleRankingResult;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.CardEvaluationResult;
import com.southwestasiafloat.backend.domain.model.DeckState;
import com.southwestasiafloat.backend.domain.model.DraftSession;
import com.southwestasiafloat.backend.domain.service.DeckStateAnalyzer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class RuleBasedDraftRankingService {

    private final DeckStateAnalyzer deckStateAnalyzer;

    public RuleBasedDraftRankingService(DeckStateAnalyzer deckStateAnalyzer) {
        this.deckStateAnalyzer = deckStateAnalyzer;
    }

    public RuleRankingResult rank(List<CardEvaluationResult> evaluations, DraftSession session) {
        if (evaluations == null || evaluations.isEmpty()) {
            return new RuleRankingResult(null, 0.0, List.of());
        }

        DeckState deckState = resolveDeckState(session);
        List<RuleRankedCandidate> rankedCandidates = new ArrayList<>();

        for (CardEvaluationResult evaluation : evaluations) {
            Card card = evaluation.getCard();
            double baseScore = evaluation.getBaseScore() != null ? evaluation.getBaseScore() : 0.0;
            double finalScore = baseScore;
            List<String> reasons = new ArrayList<>();

            reasons.add("baseScore=" + formatScore(baseScore));

            if (card != null) {
                double countBonus = countBonus(card);
                if (countBonus > 0) {
                    finalScore += countBonus;
                    reasons.add("countBonus=" + formatScore(countBonus));
                }

                double curveBonus = curveBonus(card, deckState);
                if (curveBonus != 0) {
                    finalScore += curveBonus;
                    reasons.add("curveBonus=" + formatScore(curveBonus));
                }

                double archetypeBonus = archetypeBonus(card, deckState);
                if (archetypeBonus != 0) {
                    finalScore += archetypeBonus;
                    reasons.add("archetypeBonus=" + formatScore(archetypeBonus));
                }
            }

            rankedCandidates.add(new RuleRankedCandidate(
                    card != null ? card.getName() : null,
                    baseScore,
                    roundScore(finalScore),
                    reasons
            ));
        }

        rankedCandidates.sort(Comparator.comparing(RuleRankedCandidate::finalScore).reversed());
        RuleRankedCandidate topCandidate = rankedCandidates.get(0);

        return new RuleRankingResult(
                topCandidate.cardName(),
                topCandidate.finalScore(),
                rankedCandidates
        );
    }

    private DeckState resolveDeckState(DraftSession session) {
        if (session == null) {
            return deckStateAnalyzer.analyze(List.of());
        }

        if (session.getDeckState() != null) {
            return session.getDeckState();
        }

        return deckStateAnalyzer.analyze(session.getPickedCards());
    }

    private double countBonus(Card card) {
        int count = card.getCount() != null ? card.getCount() : 1;
        return Math.max(0, count - 1) * 0.15;
    }

    private double curveBonus(Card card, DeckState deckState) {
        double bonus = 0.0;
        int cost = card.getCost() != null ? card.getCost() : 0;

        if (hasTag(deckState, "lack-early") && cost > 0 && cost <= 3) {
            bonus += 0.8;
        }

        if (hasTag(deckState, "lack-late") && cost >= 7) {
            bonus += 0.55;
        }

        if (hasTag(deckState, "early-heavy") && cost >= 5) {
            bonus += 0.2;
        }

        return bonus;
    }

    private double archetypeBonus(Card card, DeckState deckState) {
        double bonus = 0.0;
        String type = card.getType() != null ? card.getType().toLowerCase(Locale.ROOT) : "";

        if (hasTag(deckState, "control") && "order".equals(type)) {
            bonus += 0.35;
        }

        if (hasTag(deckState, "unit-heavy") && "order".equals(type)) {
            bonus += 0.2;
        }

        if (!hasTag(deckState, "unit-heavy") && !hasTag(deckState, "control") && "unit".equals(type)) {
            bonus += 0.1;
        }

        return bonus;
    }

    private boolean hasTag(DeckState deckState, String tag) {
        return deckState != null
                && deckState.getTags() != null
                && deckState.getTags().contains(tag);
    }

    private Double roundScore(double score) {
        return Math.round(score * 100.0) / 100.0;
    }

    private String formatScore(double score) {
        return String.format(Locale.US, "%.2f", score);
    }
}
