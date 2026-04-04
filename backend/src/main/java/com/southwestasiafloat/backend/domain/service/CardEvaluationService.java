package com.southwestasiafloat.backend.domain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.CardEvaluationResult;
import com.southwestasiafloat.backend.domain.model.OfferedCards;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CardEvaluationService {

    private final ObjectMapper objectMapper;

    // 全量卡库：name -> Card
    private Map<String, Card> fullCardMap = new HashMap<>();

    // 各国评分表：nation -> (cardName -> score)
    private Map<String, Map<String, Double>> nationScoreMap = new HashMap<>();

    // OCR国家名称到评分表国家名称的映射
    private static final Map<String, String> NATION_MAPPING = Map.of(
            "英国", "Britain",
            "德国", "Germany",
            "法国", "France",
            "美国", "USA",
            "苏联", "Soviet",
            "日本", "Japan",
            "意大利", "Italy",
            "波兰", "Poland",
            "芬兰", "Finland"
    );

    public CardEvaluationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        loadCards();
        loadNationScores();
    }

    public List<CardEvaluationResult> evaluate(OfferedCards offeredCards) {
        List<Card> offeredList = Arrays.asList(
                offeredCards.getCard1(),
                offeredCards.getCard2(),
                offeredCards.getCard3()
        );

        return offeredList.stream()
                .filter(Objects::nonNull)
                .map(this::evaluateSingleCard)
                .collect(Collectors.toList());
    }

    private CardEvaluationResult evaluateSingleCard(Card ocrCard) {
        String ocrName = safeTrim(ocrCard.getName());
        Integer count = ocrCard.getCount() == null ? 1 : ocrCard.getCount();

        if (ocrName == null || ocrName.isEmpty()) {
            return new CardEvaluationResult(
                    ocrCard,
                    0.0,
                    count,
                    0.0,
                    "N/A",
                    "OCR未识别到有效卡名",
                    false
            );
        }

        // 1. 从全卡库里找完整卡
        Card fullCard = fullCardMap.get(ocrName);

        if (fullCard == null) {
            return new CardEvaluationResult(
                    ocrCard,
                    0.0,
                    count,
                    0.0,
                    "cards.json",
                    "未在全量卡库中匹配到该卡",
                    false
            );
        }

        String nation = safeTrim(fullCard.getNation());
        nation = NATION_MAPPING.getOrDefault(nation, nation);
        Map<String, Double> scoreTable = nationScoreMap.getOrDefault(nation, Collections.emptyMap());

        // 2. 从对应国家评分表里找分
        Double baseScore = scoreTable.get(fullCard.getName());

        if (baseScore == null) {
            baseScore = 2.5; // 默认中庸分
            return new CardEvaluationResult(
                    fullCard,
                    baseScore,
                    count,
                    baseScore,
                    nation + ".json",
                    "已匹配到卡牌，但该卡未收录在竞技场评分表中，使用默认分",
                    true
            );
        }

        return new CardEvaluationResult(
                fullCard,
                baseScore,
                count,
                baseScore,
                nation + ".json",
                "已根据对应国家竞技场评分表完成基础评分",
                true
        );
    }

    private void loadCards() {
        try (InputStream is = new ClassPathResource("data/cards.json").getInputStream()) {
            List<Card> cards = objectMapper.readValue(is, new TypeReference<List<Card>>() {});
            this.fullCardMap = cards.stream()
                    .filter(card -> card.getName() != null && !card.getName().isBlank())
                    .collect(Collectors.toMap(
                            card -> card.getName().trim(),
                            card -> card,
                            (a, b) -> a
                    ));
        } catch (Exception e) {
            throw new RuntimeException("加载 cards.json 失败", e);
        }
    }

    private void loadNationScores() {
        List<String> nations = Arrays.asList(
                "Finland", "France", "Germany", "Italy",
                "Japan", "Poland", "Soviet", "USA", "Britain"
        );

        for (String nation : nations) {
            String path = "KardsArenaScore/" + nation + ".json";
            try (InputStream is = new ClassPathResource(path).getInputStream()) {
                Map<String, Double> scoreMap = objectMapper.readValue(
                        is,
                        new TypeReference<Map<String, Double>>() {}
                );
                nationScoreMap.put(nation, scoreMap);
            } catch (Exception e) {
                nationScoreMap.put(nation, new HashMap<>());
            }
        }
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }
}
