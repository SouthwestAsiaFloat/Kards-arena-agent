package com.southwestasiafloat.backend.LLMTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.domain.gateway.LlmGateway;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.DraftSession;
import com.southwestasiafloat.backend.domain.model.OfferedCards;
import com.southwestasiafloat.backend.domain.service.SynergyAnalyzer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


import java.util.List;
@SpringBootTest
public class SynergyTest {

    @Autowired
    private LlmGateway llmGateway;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void test_ai_can_follow_prompt() {

        // ========= 当前 session：已抓牌 =========
        Card picked1 = new Card();
        picked1.setId("0d8145975b30");
        picked1.setSeq_id("card_1467");
        picked1.setName("哈利法克斯 B Mk I");
        picked1.setNation("英国");
        picked1.setCount(1);
        picked1.setCost(7);
        picked1.setAttack(5);
        picked1.setDefense(4);
        picked1.setKeywords(List.of("轰炸"));
        picked1.setDescription("友方轰炸机部署时，随机对 1 个敌方目标造成等同于其攻击力的伤害。");
        picked1.setType("unit");

        Card picked2 = new Card();
        picked2.setId("a51840d8c162");
        picked2.setSeq_id("card_0863");
        picked2.setName("USS 约克城号");
        picked2.setNation("美国");
        picked2.setCount(1);
        picked2.setCost(5);
        picked2.setAttack(null);
        picked2.setDefense(null);
        picked2.setKeywords(List.of());
        picked2.setDescription("将 4 个“F2A 水牛”加入战场，若可能，加入前线。");
        picked2.setType("order");

        Card picked3 = new Card();
        picked3.setId("fb0aefceda8f");
        picked3.setSeq_id("card_0697");
        picked3.setName("红魔空降步兵团");
        picked3.setNation("美国");
        picked3.setCount(3);
        picked3.setCost(1);
        picked3.setAttack(1);
        picked3.setDefense(3);
        picked3.setKeywords(List.of("闪击"));
        picked3.setDescription("敌方指向或攻击本单位时，+1 花费。");
        picked3.setType("unit");

        DraftSession session = new DraftSession();
        session.setPickedCards(List.of(picked1, picked2, picked3));

        Card offer1 = new Card();
        offer1.setName("圣马可第 1 团");
        offer1.setCost(3);
        offer1.setType("unit");
        offer1.setDescription("部署：若卡组顶是海军牌，获得 +2+2 和闪击。");
        offer1.setCount(3);

        Card offer2 = new Card();
        offer2.setName("我们能做到！");
        offer2.setCost(5);
        offer2.setType("order");
        offer2.setDescription("所有友方单位和总部获得+3防御力，抽1张牌");
        offer2.setCount(1);

        Card offer3 = new Card();
        offer3.setName("地中海突袭");
        offer3.setCost(3);
        offer3.setType("order");
        offer3.setDescription("对 1 个单位造成 2 点伤害。若友方单位数大于敌方单位，改为造成 4 点伤害。");
        offer3.setCount(1);

        OfferedCards offeredCards = new OfferedCards();
        offeredCards.setCards(List.of(offer1, offer2, offer3));

        SynergyAnalyzer analyzer = new SynergyAnalyzer(llmGateway, objectMapper);

        String result = analyzer.analyze(session, offeredCards);

        System.out.println("=== AI RESULT ===");
        System.out.println(result);
    }
}
