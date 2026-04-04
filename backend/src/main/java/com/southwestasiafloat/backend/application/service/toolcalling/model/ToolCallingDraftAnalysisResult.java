package com.southwestasiafloat.backend.application.service.toolcalling.model;

import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.FinalDecision;

import java.util.List;

public record ToolCallingDraftAnalysisResult(
        List<Card> offeredCards,
        FinalDecision decision
) {
}
