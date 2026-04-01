package com.southwestasiafloat.backend.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.domain.gateway.OcrGateway;
import com.southwestasiafloat.backend.domain.model.*;
import com.southwestasiafloat.backend.domain.service.CardEvaluationService;
import com.southwestasiafloat.backend.domain.service.DraftDecisionService;
import com.southwestasiafloat.backend.domain.service.SynergyAnalyzer;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import java.util.stream.Collectors;
import java.util.ArrayList;
import com.southwestasiafloat.backend.infrastructure.repository.InMemorySessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.web.multipart.MultipartFile;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class DraftApplicationService {

    private final OcrGateway ocrGateway;
    private final ObjectMapper objectMapper;
    private final CardEvaluationService CardEvaluationService;
    private final DraftDecisionService DraftDecisionService;
    private final InMemorySessionRepository repository ;
    private final SynergyAnalyzer SynergyAnalyzer;

    public DraftApplicationService(OcrGateway ocrGateway,
                                   ObjectMapper objectMapper,
                                   CardEvaluationService cardEvaluationService,
                                   DraftDecisionService draftDecisionService,
                                   InMemorySessionRepository repository,
                                   SynergyAnalyzer synergyAnalyzer) {
        this.ocrGateway = ocrGateway;
        this.objectMapper = objectMapper;
        this.CardEvaluationService = cardEvaluationService;
        this.DraftDecisionService = draftDecisionService;
        this.repository = repository;
        this.SynergyAnalyzer = synergyAnalyzer;
    }

    public DraftAnalyzeResponse analyze(MultipartFile file) throws Exception {
        String ocrRawJson = analyzeScreenshot(file);
        log.info("OCR 原始解析结果: {}", ocrRawJson);
        List<Card> cards = parseCards(ocrRawJson);
        OfferedCards offeredCards = toOfferedCards(cards);

        // 给前端返回的卡牌名称列表，过滤掉 null 或空字符串
        List<String> cardNames = cards.stream()
                .map(Card::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toList());

        log.info("解析得到的候选卡: {}", offeredCards);

        // 先只做基础评分
        List<CardEvaluationResult> evaluations =
                CardEvaluationService.evaluate(offeredCards);
        // 返回目前抓牌回合的DraftSession
//        DraftSession session = repository.get();

       // 测试阶段, 先写一个空的session
        DraftSession session = new DraftSession();
        session.setPickedCards(new ArrayList<>());

        //调用SynergyService获取协同评分结果
        List<SynergyResult> synergyResults =
                SynergyAnalyzer.evaluateSynergy(session, offeredCards);

        //调用DraftDecisionService获取最终选牌决策
        FinalDecision decision = DraftDecisionService.decide(evaluations, synergyResults);

        return new DraftAnalyzeResponse(decision);
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

    public String analyzeScreenshot(MultipartFile file) throws Exception {
        byte[] imageBytes = file.getBytes();
        return ocrGateway.analyzeImage(imageBytes);
    }

}