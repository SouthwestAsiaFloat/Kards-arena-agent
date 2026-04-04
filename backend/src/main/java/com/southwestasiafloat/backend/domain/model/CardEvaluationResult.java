package com.southwestasiafloat.backend.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CardEvaluationResult {
    private Card card;              // 完整卡信息
    private Double baseScore;       // 评分表基础分
    private Integer count;              // 这一抓的数量
    private Double adjustedScore;   // 当前先等于 baseScore
    private String source;          // 分数来源，比如 Germany.json
    private String comment;         // 评语
    private boolean matched;        // 是否成功命中知识库/评分表
}

