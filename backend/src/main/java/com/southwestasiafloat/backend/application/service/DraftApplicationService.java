package com.southwestasiafloat.backend.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.southwestasiafloat.backend.domain.gateway.OcrGateway;
import com.southwestasiafloat.backend.domain.model.Card;
import com.southwestasiafloat.backend.domain.model.CardEvaluationResult;
import com.southwestasiafloat.backend.domain.model.OfferedCards;
import com.southwestasiafloat.backend.domain.service.CardEvaluationService;
import com.southwestasiafloat.backend.domain.service.DraftDecisionService;
import com.southwestasiafloat.backend.dto.request.DraftAnalyzeRequest;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class DraftApplicationService {

    private final OcrGateway ocrGateway;
    private final ObjectMapper objectMapper;
    private final CardEvaluationService CardEvaluationService;
    private final DraftDecisionService DraftDecisionService;

    public DraftApplicationService(OcrGateway ocrGateway, ObjectMapper objectMapper, CardEvaluationService cardEvaluationService, DraftDecisionService draftDecisionService) {
        this.ocrGateway = ocrGateway;
        this.objectMapper = objectMapper;
        this.CardEvaluationService = cardEvaluationService;
        this.DraftDecisionService = draftDecisionService;
    }

    public DraftAnalyzeResponse analyze(DraftAnalyzeRequest request) {
        String ocrRawJson = ocrGateway.analyzeImage(request.screenshotBase64());
        List<Card> cards = parseCards(ocrRawJson);
        OfferedCards offeredCards = toOfferedCards(cards);

        // 给前端返回的卡牌名称列表，过滤掉 null 或空字符串
        List<String> cardNames = cards.stream()
                .map(Card::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toList());
        log.info("当前候选卡: {}", cardNames);

        // 先只做基础评分
        List<CardEvaluationResult> evaluations =
                CardEvaluationService.evaluate(offeredCards);

        // 先简单选最高分
        CardEvaluationResult bestPick =
                DraftDecisionService.pickBest(evaluations);


        return new DraftAnalyzeResponse(ocrRawJson, cardNames);
    }

    private List<Card> parseCards(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, new TypeReference<List<Card>>() {});
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private OfferedCards toOfferedCards(List<Card> cards) {
        OfferedCards offeredCards = new OfferedCards();

        if (cards.size() > 0) {
            offeredCards.setCard1(cards.get(0));
        }
        if (cards.size() > 1) {
            offeredCards.setCard2(cards.get(1));
        }
        if (cards.size() > 2) {
            offeredCards.setCard3(cards.get(2));
        }

        return offeredCards;
    }

}