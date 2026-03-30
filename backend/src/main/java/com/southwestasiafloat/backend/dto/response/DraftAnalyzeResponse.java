package com.southwestasiafloat.backend.dto.response;

import java.util.List;

public record DraftAnalyzeResponse(
        String ocrRawJson,
        List<String> suggestions
) {
}
