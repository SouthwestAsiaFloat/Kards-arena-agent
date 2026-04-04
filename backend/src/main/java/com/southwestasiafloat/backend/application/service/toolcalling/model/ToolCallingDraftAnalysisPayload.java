package com.southwestasiafloat.backend.application.service.toolcalling.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCallingDraftAnalysisPayload(
        String recommendedCardName,
        String reason,
        Double finalScore,
        String decisionSource
) {
}
