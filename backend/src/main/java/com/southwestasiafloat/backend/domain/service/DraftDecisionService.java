package com.southwestasiafloat.backend.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.domain.model.CardEvaluationResult;
import com.southwestasiafloat.backend.domain.model.FinalDecision;
import com.southwestasiafloat.backend.domain.model.SynergyResult;
import com.southwestasiafloat.backend.infrastructure.client.LangChain4jLlmClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DraftDecisionService {

    private final LangChain4jLlmClient llmClient;
    private final ObjectMapper objectMapper;

    // 你可以微调这个权重
    // 基础分更重要，协同分辅助
    private static final double BASE_SCORE_WEIGHT = 0.8;
    private static final double SYNERGY_SCORE_WEIGHT = 0.2;

    public DraftDecisionService(LangChain4jLlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public FinalDecision decide(List<CardEvaluationResult> evaluations,
                                List<SynergyResult> synergyResults) {

        if (evaluations == null || evaluations.isEmpty()) {
            return new FinalDecision(
                    null,
                    "没有可供决策的候选牌。",
                    "rule",
                    0.0
            );
        }

        // 1. 先把 synergyResults 转成 map，便于按卡名查协同分
        Map<String, SynergyResult> synergyMap = synergyResults == null
                ? new HashMap<>()
                : synergyResults.stream()
                .collect(Collectors.toMap(
                        SynergyResult::getCardName,
                        s -> s,
                        (a, b) -> a
                ));

        // 2. 计算每张牌的 finalScore
        List<CandidateScore> candidates = new ArrayList<>();
        for (CardEvaluationResult eval : evaluations) {
            double baseScore = eval.getBaseScore() != null ? eval.getBaseScore() : 0.0;

            SynergyResult synergy = synergyMap.get(eval.getCard().getName());
            double synergyBonus = synergy != null ? synergy.getSynergyBonus() : 0.0;

            // 强调基础分，协同分辅助
            double finalScore = baseScore * BASE_SCORE_WEIGHT + synergyBonus * SYNERGY_SCORE_WEIGHT;

            candidates.add(new CandidateScore(
                    eval,
                    synergyBonus,
                    finalScore,
                    synergy != null ? Arrays.toString(synergy.getComment()) : "无明显协同"
            ));
        }
          // 3. fallback 方案：本地 finalScore 最高
        CandidateScore bestByFinalScore = candidates.stream()
                .max(Comparator.comparing(CandidateScore::getFinalScore))
                .orElse(null);

        String prompt = buildDecisionPrompt(candidates);

        try {
            String llmAnswer = llmClient.analyzeDraft(prompt);

            LlmDecision llmDecision = parseLlmDecision(llmAnswer);

            if (llmDecision == null || llmDecision.getRecommendedCardName() == null) {
                return fallbackDecision(bestByFinalScore, "LLM 返回内容无法解析，已回退到 finalScore 最高方案。");
            }

            // 4. 用 AI 返回的卡名，找回真正的推荐卡对象
            CandidateScore aiRecommended = findCandidateByName(candidates, llmDecision.getRecommendedCardName());

            if (aiRecommended == null) {
                return fallbackDecision(
                        bestByFinalScore,
                        "LLM 推荐的卡名未能匹配候选牌，已回退到 finalScore 最高方案。"
                );
            }

            return new FinalDecision(
                    aiRecommended.getEvaluation(),
                    llmDecision.getReason(),
                    "llm",
                    aiRecommended.getFinalScore()
            );

        } catch (Exception e) {
            return fallbackDecision(
                    bestByFinalScore,
                    "LLM 调用失败，已回退到 finalScore 最高方案。错误信息：" + e.getMessage()
            );
        }
    }

    private FinalDecision fallbackDecision(CandidateScore fallback, String reason) {
        return new FinalDecision(
                fallback != null ? fallback.getEvaluation() : null,
                reason,
                "rule",
                fallback != null ? fallback.getFinalScore() : 0.0
        );
    }

    private CandidateScore findCandidateByName(List<CandidateScore> candidates, String cardName) {
        if (cardName == null) {
            return null;
        }

        // 先精确匹配
        for (CandidateScore c : candidates) {
            if (c.getEvaluation().getCard().getName().equals(cardName)) {
                return c;
            }
        }

        // 再做宽松匹配
        String normalized = normalize(cardName);
        for (CandidateScore c : candidates) {
            if (normalize(c.getEvaluation().getCard().getName()).equals(normalized)) {
                return c;
            }
        }

        return null;
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", "");
    }

    private LlmDecision parseLlmDecision(String llmAnswer) {
        try {
            JsonNode root = objectMapper.readTree(llmAnswer);
            String recommendedCardName = root.path("recommendedCardName").asText(null);
            String reason = root.path("reason").asText("未提供理由");
            return new LlmDecision(recommendedCardName, reason);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildDecisionPrompt(List<CandidateScore> candidates) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是 KARDS 竞技场选牌助手。\n");
        sb.append("现在有 3 张候选牌，请根据竞技场基础评分、协同加分和综合 finalScore，选出最推荐的一张。\n\n");

        sb.append("决策规则：\n");
        sb.append("1. finalScore 是重要参考，且应强烈影响你的选择。\n");
        sb.append("2. finalScore = 基础分 * ").append(BASE_SCORE_WEIGHT)
                .append(" + 协同分 * ").append(SYNERGY_SCORE_WEIGHT).append("\n");
        sb.append("3. 基础分比协同分更重要。\n");
        sb.append("4. 如果某张牌的 finalScore 明显高于其他牌，通常应优先推荐它。\n");
        sb.append("5. 如果几张牌的 finalScore 很接近，你可以结合牌质、稳定性、上限、曲线价值、竞技场泛用性来最终拍板。\n");
        sb.append("6. 你必须只从给定候选牌中选 1 张，不允许输出候选之外的牌。\n\n");

        sb.append("候选牌信息如下：\n");
        for (CandidateScore c : candidates) {
            CardEvaluationResult eval = c.getEvaluation();

            sb.append("- 卡名: ").append(eval.getCard().getName()).append("\n");
            sb.append("  基础分: ").append(eval.getBaseScore()).append("\n");
            sb.append("  数量: ").append(eval.getCount()).append("\n");
            sb.append("  协同分: ").append(c.getSynergyBonus()).append("\n");
            sb.append("  finalScore: ").append(String.format("%.2f", c.getFinalScore())).append("\n");
            sb.append("  协同说明: ").append(c.getSynergyComment()).append("\n");
        }

        sb.append("\n输出要求：\n");
        sb.append("你必须严格输出 JSON，不要输出任何额外解释，不要加 markdown 代码块。\n");
        sb.append("格式如下：\n");
        sb.append("{\n");
        sb.append("  \"recommendedCardName\": \"卡牌名称\",\n");
        sb.append("  \"reason\": \"简洁说明推荐理由\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 内部类：保存每张候选牌的综合评分信息
     */
    private static class CandidateScore {
        private final CardEvaluationResult evaluation;
        private final double synergyBonus;
        private final double finalScore;
        private final String synergyComment;

        public CandidateScore(CardEvaluationResult evaluation,
                              double synergyBonus,
                              double finalScore,
                              String synergyComment) {
            this.evaluation = evaluation;
            this.synergyBonus = synergyBonus;
            this.finalScore = finalScore;
            this.synergyComment = synergyComment;
        }

        public CardEvaluationResult getEvaluation() {
            return evaluation;
        }

        public double getSynergyBonus() {
            return synergyBonus;
        }

        public double getFinalScore() {
            return finalScore;
        }

        public String getSynergyComment() {
            return synergyComment;
        }
    }

    /**
     * 内部类：承接 LLM 返回结果
     */
    private static class LlmDecision {
        private final String recommendedCardName;
        private final String reason;

        public LlmDecision(String recommendedCardName, String reason) {
            this.recommendedCardName = recommendedCardName;
            this.reason = reason;
        }

        public String getRecommendedCardName() {
            return recommendedCardName;
        }

        public String getReason() {
            return reason;
        }
    }
}