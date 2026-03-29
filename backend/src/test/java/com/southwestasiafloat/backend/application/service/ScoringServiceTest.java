package com.southwestasiafloat.backend.application.service;

import com.southwestasiafloat.backend.application.service.skill.BaseCardEvaluationSkill;
import com.southwestasiafloat.backend.application.service.skill.CurveAnalysisSkill;
import com.southwestasiafloat.backend.application.service.skill.DraftKnowledge;
import com.southwestasiafloat.backend.application.service.skill.ExplanationSkill;
import com.southwestasiafloat.backend.application.service.skill.NationBiasSkill;
import com.southwestasiafloat.backend.application.service.skill.RiskEvaluationSkill;
import com.southwestasiafloat.backend.application.service.skill.ScoringSummary;
import com.southwestasiafloat.backend.application.service.skill.SynergyAnalysisSkill;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoringServiceTest {

    @Test
    void scoreAggregatesAllSkills() {
        ScoringService scoringService = new ScoringService(List.of(
                new BaseCardEvaluationSkill(),
                new CurveAnalysisSkill(),
                new SynergyAnalysisSkill(),
                new NationBiasSkill(),
                new RiskEvaluationSkill(),
                new ExplanationSkill()
        ));

        ScoringSummary summary = scoringService.score(
                List.of("Dragon Rider", "Arcane Draw", "Shield Bearer"),
                new DraftKnowledge(3, false, true)
        );

        assertTrue(summary.totalScore() > 0);
        assertFalse(summary.reasons().isEmpty());
    }
}

