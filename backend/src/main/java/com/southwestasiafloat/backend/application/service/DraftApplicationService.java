package com.southwestasiafloat.backend.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.southwestasiafloat.backend.domain.gateway.OcrGateway;
import com.southwestasiafloat.backend.dto.request.DraftAnalyzeRequest;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DraftApplicationService {

    private final OcrGateway ocrGateway;
    private final ObjectMapper objectMapper;

    public DraftApplicationService(OcrGateway ocrGateway, ObjectMapper objectMapper) {
        this.ocrGateway = ocrGateway;
        this.objectMapper = objectMapper;
    }

    public DraftAnalyzeResponse analyze(DraftAnalyzeRequest request) {
        String ocrRawJson = ocrGateway.analyzeImage(request.screenshotBase64());
        List<String> cardNames = extractCardNames(ocrRawJson);
        return new DraftAnalyzeResponse(ocrRawJson, cardNames);
    }

    private List<String> extractCardNames(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode cards = root.path("cards");
            if (!cards.isArray()) {
                return Collections.emptyList();
            }

            List<String> names = new ArrayList<>();
            for (JsonNode card : cards) {
                String name = card.path("name").asText("").trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
            return names;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}