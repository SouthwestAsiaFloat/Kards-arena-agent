package com.southwestasiafloat.backend.dto.request;

/**
 * 草稿分析请求 DTO。
 */

import java.util.List;

public record DraftAnalyzeRequest(
        // 纯截图
        String screenshotBase64
) {
}

