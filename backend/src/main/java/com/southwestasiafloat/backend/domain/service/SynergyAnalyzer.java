package com.southwestasiafloat.backend.domain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.domain.gateway.LlmGateway;
import com.southwestasiafloat.backend.domain.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SynergyAnalyzer {

    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    public SynergyAnalyzer(LlmGateway llmGateway, ObjectMapper objectMapper) {
        this.llmGateway = llmGateway;
        this.objectMapper = objectMapper;
    }

    public List<SynergyResult> evaluateSynergy(
            DraftSession session,
            OfferedCards offeredCards
    ) {
        String prompt = buildPrompt(session, offeredCards);
        String response = llmGateway.analyzeDraft(prompt);

        try {
            log.info("SynergyAnalyzer LLM 返回: {}", response);
            return objectMapper.readValue(
                    response,
                    new TypeReference<List<SynergyResult>>() {}
            );
        } catch (Exception e) {
            throw new RuntimeException("解析 SynergyAnalyzer 的 LLM 返回失败，response=" + response, e);
        }
    }

    // 测试
    public String analyze(DraftSession session, OfferedCards offeredCards) {
        String prompt = buildPrompt(session, offeredCards);

        System.out.println("=== PROMPT ===");
        System.out.println(prompt);

        return llmGateway.analyzeDraft(prompt);
    }

    private String buildPrompt(DraftSession session, OfferedCards offeredCards) {

        StringBuilder sb = new StringBuilder();

        sb.append("你是一个KARDS竞技场选牌分析助手。\n");
        sb.append("请根据当前卡组和候选卡牌，分析每张候选卡的协同情况。\n");
        sb.append("不要编造不存在的卡牌效果。\n\n");

        sb.append("【当前已选卡牌】\n");

        for (Card card : session.getPickedCards()) {
            sb.append("- ")
                    .append(card.getName())
                    .append(" | cost=")
                    .append(card.getCost())
                    .append(" | type=")
                    .append(card.getType())
                    .append("\n")
                    .append(" | desc=")
                    .append(card.getDescription())
                    .append("\n")
                    .append(" | count=")
                    .append(card.getCount())
                    .append("\n");
        }

        sb.append("\n【候选卡牌】\n");

        for (Card card : offeredCards.getCards()) {
            sb.append("- ")
                    .append(card.getName())
                    .append(" | cost=")
                    .append(card.getCost())
                    .append(" | type=")
                    .append(card.getType())
                    .append(" | desc=")
                    .append(card.getDescription())
                    .append("\n")
                    .append(" | count=")
                    .append(card.getCount())
                    .append("\n");
        }

        sb.append("\n请对每张候选卡分析：\n");
        sb.append("1. 是否补强当前卡组费用曲线\n");
        sb.append("2. 是否补充卡组缺失功能（前期/单解/群解等）\n");
        sb.append("3. 是否与已有卡牌形成联动\n");
        sb.append("4. 是否存在冲突\n");
        sb.append("5. 给出一个从-3到3的协同评分\n");
        sb.append("6. 务必关注可抓卡牌的数量，不同数量会影响价值\n\n");

        sb.append("请严格返回 JSON 数组，不要输出任何额外解释。\n");
        sb.append("格式示例：\n");
        sb.append("""
                [
                  {
                    "id": "0d8145975b30",
                    "cardName": "哈利法克斯 B Mk I",
                    "synergyScore": 2.5,
                    "comment": "这张卡与当前卡组中的多张低费单位形成了良好的费用曲线补强，同时其部署效果可以与已有的 USS 约克城号产生联动，提升整体战术灵活性。",
                    "count": 2
                  }
                ]
                """);

        return sb.toString();
    }
}
