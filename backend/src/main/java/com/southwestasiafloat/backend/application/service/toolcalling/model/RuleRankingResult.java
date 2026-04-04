package com.southwestasiafloat.backend.application.service.toolcalling.model;

import java.util.List;

public record RuleRankingResult(
        String topCardName,
        Double topFinalScore,
        List<RuleRankedCandidate> candidates
) {
}
