package com.southwestasiafloat.backend.application.service;

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

class DraftAgentServiceTest {

    @Test
    void analyzeBuildsAdvice() {
        OcrGateway ocrGateway = image -> "attack and draw";
        ScoringService scoringService = new ScoringService(List.of(
                new BaseCardEvaluationSkill(),
                new CurveAnalysisSkill(),
                new SynergyAnalysisSkill(),
                new NationBiasSkill(),
                new RiskEvaluationSkill(),
                new ExplanationSkill()
        ));

        DraftAgentService service = new DraftAgentService(
                ocrGateway,
                new KnowledgeService(),
                scoringService,
                new LlmService()
        );

        DraftAnalyzeResponse response = service.analyze(
                new DraftAnalyzeRequest("base64", List.of("Dragon Rider", "Shield Bearer", "Arcane Draw"))
        );

        assertEquals("attack and draw", response.recognizedText());
        assertFalse(response.suggestions().isEmpty());
    }
}
