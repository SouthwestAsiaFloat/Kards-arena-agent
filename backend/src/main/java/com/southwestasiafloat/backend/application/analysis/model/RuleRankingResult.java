package com.southwestasiafloat.backend.application.analysis.model;

/**
 * 规则排序结果模型。
 */

import java.util.List;

public record RuleRankingResult(
        String topCardName,
        Double topFinalScore,
        List<RuleRankedCandidate> candidates
) {
}
