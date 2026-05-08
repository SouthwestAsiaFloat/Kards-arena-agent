package com.southwestasiafloat.backend.domain.model;

/**
 * 单张卡牌的评分结果模型。
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CardEvaluationResult {
    private Card card;              // 完整卡牌信息
    private Double baseScore;       // 评分表基础分
    private Integer count;          // 这一抓的数量
    private Double adjustedScore;   // 当前先与 baseScore 保持一致
    private String source;          // 分数来源，例如 Germany.json
    private String comment;         // 说明文字
    private boolean matched;        // 是否成功命中知识库或评分表
}

