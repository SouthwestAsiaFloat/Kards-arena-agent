package com.southwestasiafloat.backend.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeckState {

    // 费用曲线（key: cost, value: count）
    Map<Integer, Integer> costCurve;

    int unitCount;
    int orderCount;

    int totalCards;

    // 阶段分析
    int earlyCount;   // 1-3费
    int midCount;     // 4-6费
    int lateCount;    // 7+

    // 标签（给LLM用）
    List<String> tags;
}

