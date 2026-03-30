package com.southwestasiafloat.backend.infrastructure.client;

import com.southwestasiafloat.backend.domain.gateway.OcrGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
public class OcrHttpClient implements OcrGateway {

    private final WebClient webClient;
    private final String analyzePath;

    public OcrHttpClient(
            @Value("${ocr.base-url:http://127.0.0.1:8000}") String baseUrl,
            @Value("${ocr.analyze-path:/analyze}") String analyzePath
    ) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.analyzePath = analyzePath;
    }

    @Override
    public String analyzeImage(String imageBase64) {
        // Keep request key in snake_case to match the Python service naming style.
        Map<String, String> payload = Map.of("image_base64", imageBase64);

        return webClient.post()
                .uri(analyzePath)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .blockOptional()
                .orElse("{}");
    }
}
