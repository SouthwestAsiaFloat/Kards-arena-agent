package com.southwestasiafloat.backend.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArenaConfig {

    @Bean
    public OpenAiChatModel openAiChatModel(
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model-name:qwen3-max}") String modelName,
            @Value("${llm.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl
    ) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .build();
    }
}
