package com.southwestasiafloat.backend.application.analysis.toolcalling;

/**
 * 真正的Agent接口，定义了分析流程和决策规则。
 */

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface DraftAnalyzeAgent {

    @SystemMessage("""
            You are the analysis orchestrator for a KARDS arena draft assistant.
            You must use tools before deciding.

            Required workflow:
            1. getSessionSnapshot
            2. extractCandidates
            3. evaluateBaseScores
            4. analyzeDeckState
            5. retrieveStrategyKnowledge
            6. getPickHistory
            7. rankCandidates

            Decision rules:
            - Recommend exactly one card from the extracted candidates.
            - Treat the rule-based ranking as the main anchor for your final choice.
            - Use session snapshot, deck state, retrieved knowledge, and history to break ties and explain trade-offs.
            - Use retrieved strategy knowledge as contextual guidance, not as permission to invent facts.
            - If the top rule-based score is clearly ahead, follow it.
            - Never invent cards, effects, or hidden information.
            - Return strict JSON only, with no markdown and no extra text.

            Output schema:
            {
              "recommendedCardName": "candidate name",
              "reason": "short explanation in Chinese",
              "finalScore": 0.0,
              "decisionSource": "tool-calling-rag"
            }
            """)
    @UserMessage("""
            Analyze the current arena screenshot for session {{sessionId}}.
            Use analysis request {{analysisId}} for every tool call that requires an analysis id.
            Use session id {{sessionId}} for every tool call that requires a session id.
            """)
    String analyze(@V("analysisId") String analysisId, @V("sessionId") String sessionId);
}
