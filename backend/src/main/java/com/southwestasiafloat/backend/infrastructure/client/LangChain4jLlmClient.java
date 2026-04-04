package com.southwestasiafloat.backend.infrastructure.client;

import com.southwestasiafloat.backend.domain.gateway.LlmGateway;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

@Service
public class LangChain4jLlmClient implements LlmGateway {

    private final OpenAiChatModel model;

    public LangChain4jLlmClient(OpenAiChatModel model) {
        this.model = model;
    }

    @Override
    public String analyzeDraft(String prompt) {
        return model.chat(prompt);
    }
}
