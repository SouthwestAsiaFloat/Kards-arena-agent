package com.southwestasiafloat.backend.application.analysis.model;

/**
 * 工具调用分析载荷模型。
 */

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCallingDraftAnalysisPayload(
        String recommendedCardName,
        String reason,
        Double finalScore,
        String decisionSource
) {
}
