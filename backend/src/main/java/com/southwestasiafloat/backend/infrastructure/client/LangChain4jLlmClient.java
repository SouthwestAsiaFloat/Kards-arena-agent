package com.southwestasiafloat.backend.infrastructure.client;

import com.southwestasiafloat.backend.domain.gateway.LlmGateway;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
public class LangChain4jLlmClient implements LlmGateway {

    private final OpenAiChatModel model;

    public LangChain4jLlmClient(
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model-name:qwen3-max}") String modelName,
            @Value("${llm.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl
    ) {
        this.model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public String analyzeDraft(String prompt) {
        return model.chat(prompt);
    }
}