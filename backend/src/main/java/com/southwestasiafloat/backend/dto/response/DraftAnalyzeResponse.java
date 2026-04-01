package com.southwestasiafloat.backend.dto.response;
import com.southwestasiafloat.backend.domain.model.FinalDecision;

import java.util.List;

public record DraftAnalyzeResponse(
        FinalDecision decision
) {
}
