package com.southwestasiafloat.backend.infrastructure.client;

import com.southwestasiafloat.backend.domain.gateway.OcrGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class OcrHttpClient implements OcrGateway {

    private final WebClient webClient;
    private final String ocrPath;

    public OcrHttpClient(
            @Value("${ocr.base-url:http://127.0.0.1:8000}") String baseUrl,
            @Value("${ocr.path:/ocr}") String ocrPath
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.ocrPath = ocrPath;
    }

    @Override
    public String analyzeImage(byte[] imageBytes) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        builder.part("file", new ByteArrayResource(imageBytes) {
                    @Override
                    public String getFilename() {
                        return "screenshot.png";
                    }
                })
                .contentType(MediaType.IMAGE_PNG);

        return webClient.post()
                .uri(ocrPath)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}