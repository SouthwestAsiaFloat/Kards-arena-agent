package com.southwestasiafloat.backend.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.domain.gateway.LlmGateway;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.CardEvaluationResult;
import com.southwestasiafloat.backend.domain.model.DraftSession;
import com.southwestasiafloat.backend.domain.model.FinalDecision;
import com.southwestasiafloat.backend.domain.model.OfferedCards;
import com.southwestasiafloat.backend.domain.model.SynergyResult;
import com.southwestasiafloat.backend.infrastructure.client.LangChain4jLlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;

class DraftFullChainTest {

    private CardEvaluationService cardEvaluationService;
    private SynergyAnalyzer synergyAnalyzer;
    private DraftDecisionService draftDecisionService;

    private LlmGateway llmGateway;
    private LangChain4jLlmClient llmClient;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();

        // 真 service
        cardEvaluationService = new CardEvaluationService(objectMapper);
        cardEvaluationService.init();

        // mock LLM 依赖
        llmGateway = Mockito.mock(LlmGateway.class);
        llmClient = Mockito.mock(LangChain4jLlmClient.class);

        synergyAnalyzer = new SynergyAnalyzer(llmGateway, objectMapper);
        draftDecisionService = new DraftDecisionService(llmClient);
    }

    @Test
    @DisplayName("从候选卡数组到最终 FinalDecision 的完整链路测试")
    void shouldRunFullChainFromOfferedCardsToFinalDecision() {
        // =========================
        // 1. 模拟 OCR 输出的候选卡数组
        // =========================
        Card ocrCard1 = new Card();
        ocrCard1.setName("哈利法克斯 B Mk I");
        ocrCard1.setCount(1);

        Card ocrCard2 = new Card();
        ocrCard2.setName("USS 约克城号");
        ocrCard2.setCount(2);

        Card ocrCard3 = new Card();
        ocrCard3.setName("红魔空降步兵团");
        ocrCard3.setCount(1);

        OfferedCards offeredCards = new OfferedCards();
        offeredCards.setCard1(ocrCard1);
        offeredCards.setCard2(ocrCard2);
        offeredCards.setCard3(ocrCard3);

        // 如果你的 OfferedCards 有 setCards(List<Card>)，可以一起补上
        // offeredCards.setCards(List.of(ocrCard1, ocrCard2, ocrCard3));

        // =========================
        // 2. 模拟当前已抓卡组（给 SynergyAnalyzer 用）
        // =========================
        DraftSession session = new DraftSession(sessionId);
        List<Card> pickedCards = new ArrayList<>();

        Card picked1 = new Card();
        picked1.setName("第101轻步兵团");
        picked1.setCost(1);
        picked1.setType("unit");
        picked1.setDescription("前期站场单位");
        picked1.setCount(1);

        Card picked2 = new Card();
        picked2.setName("战术打击");
        picked2.setCost(3);
        picked2.setType("order");
        picked2.setDescription("中期解场");
        picked2.setCount(1);

        Card picked3 = new Card();
        picked3.setName("F2A 水牛");
        picked3.setCost(2);
        picked3.setType("unit");
        picked3.setDescription("低费飞机");
        picked3.setCount(2);

        pickedCards.add(picked1);
        pickedCards.add(picked2);
        pickedCards.add(picked3);

        session.setPickedCards(pickedCards);

        // =========================
        // 3. 真跑基础评分
        // =========================
        List<CardEvaluationResult> evaluations = cardEvaluationService.evaluate(offeredCards);

        assertNotNull(evaluations);
        assertEquals(3, evaluations.size());

        // =========================
        // 4. mock 协同分析 LLM 返回 JSON
        //    这里假设你 SynergyAnalyzer 已经能 parse JSON
        // =========================
        String synergyJson = """
                [
                  {
                    "id": "0d8145975b30",
                    "cardName": "哈利法克斯 B Mk I",
                    "synergyScore": 0.5,
                    "comment": "后期补强，但当前曲线偏重，协同一般。",
                    "count": 1
                  },
                  {
                    "id": "a51840d8c162",
                    "cardName": "USS 约克城号",
                    "synergyScore": 1.5,
                    "comment": "能补强中期节奏，且数量为2，稳定性和联动性更好。",
                    "count": 2
                  },
                  {
                    "id": "fb0aefceda8f",
                    "cardName": "红魔空降步兵团",
                    "synergyScore": -0.2,
                    "comment": "虽能补前期，但整体联动一般。",
                    "count": 1
                  }
                ]
                """;

        Mockito.when(llmGateway.analyzeDraft(anyString()))
                .thenReturn(synergyJson);

        // 真跑 SynergyAnalyzer
        List<SynergyResult> synergyResults = synergyAnalyzer.evaluateSynergy(session, offeredCards);

        assertNotNull(synergyResults);
        assertEquals(3, synergyResults.size());

        // =========================
        // 5. mock 最终决策 LLM 输出
        // =========================
        Mockito.when(llmClient.analyzeDraft(anyString()))
                .thenReturn("""
                        推荐卡：USS 约克城号
                        理由：这张卡的竞技场基础分最高，同时数量为2，稳定性更强；并且它与当前卡组的中期节奏和已有单位体系协同性最好，因此是本轮最优选择。
                        """);

        // 真跑 Final Decision
        FinalDecision finalDecision = draftDecisionService.decide(evaluations, synergyResults);

        // =========================
        // 6. 断言最终结果
        // =========================
        assertNotNull(finalDecision);
        assertNotNull(finalDecision.getRecommendedCard());
        assertNotNull(finalDecision.getLlmReason());

        // 当前 DraftDecisionService 第一版还是按规则最高分 bestByRule 返回推荐卡
        assertEquals("USS 约克城号", finalDecision.getRecommendedCard().getCard().getName());
        assertEquals("llm", finalDecision.getDecisionSource());
        assertTrue(finalDecision.getLlmReason().contains("USS 约克城号"));

        // =========================
        // 7. 打印链路结果，方便你看
        // =========================
        System.out.println("========== OCR候选卡 ==========");
        System.out.println(offeredCards);

        System.out.println("========== 基础评分结果 ==========");
        for (CardEvaluationResult evaluation : evaluations) {
            System.out.println(evaluation);
        }

        System.out.println("========== 协同分析结果 ==========");
        for (SynergyResult synergyResult : synergyResults) {
            System.out.println(synergyResult);
        }

        System.out.println("========== FinalDecision ==========");
        System.out.println(finalDecision);
    }
}
