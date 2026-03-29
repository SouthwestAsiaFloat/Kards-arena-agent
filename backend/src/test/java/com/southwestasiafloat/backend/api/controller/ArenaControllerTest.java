package com.southwestasiafloat.backend.controller;

import com.southwestasiafloat.backend.application.service.DraftAgentService;
import com.southwestasiafloat.backend.application.service.skill.BaseCardEvaluationSkill;
import com.southwestasiafloat.backend.application.service.skill.CurveAnalysisSkill;
import com.southwestasiafloat.backend.application.service.skill.ExplanationSkill;
import com.southwestasiafloat.backend.application.service.skill.NationBiasSkill;
import com.southwestasiafloat.backend.application.service.skill.RiskEvaluationSkill;
import com.southwestasiafloat.backend.application.service.skill.SynergyAnalysisSkill;
import com.southwestasiafloat.backend.domain.gateway.OcrGateway;
import com.southwestasiafloat.backend.dto.request.DraftAnalyzeRequest;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ArenaControllerTest {

    @Test
    void analyzeReturnsResponse() {
        OcrGateway ocrGateway = image -> "mock-ocr-result";
        ScoringService scoringService = new ScoringService(List.of(
                new BaseCardEvaluationSkill(),
                new CurveAnalysisSkill(),
                new SynergyAnalysisSkill(),
                new NationBiasSkill(),
                new RiskEvaluationSkill(),
                new ExplanationSkill()
        ));
        DraftAgentService draftAgentService = new DraftAgentService(
                ocrGateway,
                new KnowledgeService(),
                scoringService,
                new LlmService()
        );
        ArenaApplicationService service = new ArenaApplicationService(draftAgentService);
        ArenaController controller = new ArenaController(service);

        DraftAnalyzeRequest request = new DraftAnalyzeRequest("base64-image", List.of("Card A", "Card B"));
        DraftAnalyzeResponse response = controller.analyze(request);

        assertEquals("mock-ocr-result", response.recognizedText());
        assertFalse(response.suggestions().isEmpty());
    }
}
