package com.southwestasiafloat.backend.application.service.toolcalling.model;

import java.util.List;

public record RuleRankedCandidate(
        String cardName,
        Double baseScore,
        Double finalScore,
        List<String> reasons
) {
}
