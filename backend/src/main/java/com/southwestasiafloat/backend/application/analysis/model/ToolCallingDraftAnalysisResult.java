package com.southwestasiafloat.backend.application.analysis.model;

/**
 * 工具调用分析结果模型。
 */

import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.FinalDecision;

import java.util.List;

public record ToolCallingDraftAnalysisResult(
        List<Card> offeredCards,
        FinalDecision decision
) {
}
