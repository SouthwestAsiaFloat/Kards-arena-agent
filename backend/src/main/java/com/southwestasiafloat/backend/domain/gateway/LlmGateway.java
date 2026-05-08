package com.southwestasiafloat.backend.domain.gateway;

/**
 * 大语言模型网关接口。
 */

public interface LlmGateway {
    String analyzeDraft(String prompt);
}

