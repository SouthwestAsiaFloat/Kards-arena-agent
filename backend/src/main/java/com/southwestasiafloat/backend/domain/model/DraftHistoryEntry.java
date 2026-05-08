package com.southwestasiafloat.backend.domain.model;

/**
 * 草稿历史记录，保存每次分析与确认结果。
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DraftHistoryEntry {

    private Integer pickNo;
    private String analyzedAt;
    private List<Card> offeredCards = new ArrayList<>();
    private Card recommendedCard;
    private Card pickedCard;
    private String decisionSource;
    private Double finalScore;
    private String reason;
    private String status;
}
