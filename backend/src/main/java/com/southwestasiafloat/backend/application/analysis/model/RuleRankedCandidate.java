package com.southwestasiafloat.backend.application.analysis.model;

/**
 * 规则排序候选项模型。
 */

import java.util.List;

public record RuleRankedCandidate(
        String cardName,
        Double baseScore,
        Double finalScore,
        List<String> reasons
) {
}
