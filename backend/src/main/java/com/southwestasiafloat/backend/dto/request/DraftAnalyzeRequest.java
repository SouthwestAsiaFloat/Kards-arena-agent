package com.southwestasiafloat.backend.dto.request;

import java.util.List;

public record DraftAnalyzeRequest(
        String screenshotBase64,
        List<String> cards
) {
}

