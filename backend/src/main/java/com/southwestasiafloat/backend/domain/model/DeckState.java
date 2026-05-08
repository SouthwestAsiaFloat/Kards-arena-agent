package com.southwestasiafloat.backend.domain.model;

/**
 * 牌组状态领域模型。
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeckState {

    // 费用曲线（费用 -> 数量）
    Map<Integer, Integer> costCurve;

    int unitCount;
    int orderCount;

    int totalCards;

    // 阶段分析
    int earlyCount;   // 1-3费
    int midCount;     // 4-6费
    int lateCount;    // 7+

    // 标签（供 LLM 使用）
    List<String> tags;
}

