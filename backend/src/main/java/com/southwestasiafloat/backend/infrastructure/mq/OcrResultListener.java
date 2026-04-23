package com.southwestasiafloat.backend.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.southwestasiafloat.backend.application.service.AsyncDraftApplicationService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class OcrResultListener {

    private final AsyncDraftApplicationService asyncDraftApplicationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public OcrResultListener(AsyncDraftApplicationService asyncDraftApplicationService,
                             ObjectMapper objectMapper,
                             MeterRegistry meterRegistry) {
        this.asyncDraftApplicationService = asyncDraftApplicationService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @RabbitListener(
            queues = "${arena.ocr.async.result-queue}",
            concurrency = "${arena.ocr.async.result-listener-concurrency:2-8}"
    )
    public void onOcrResult(Message rawMessage) {
        String body = new String(rawMessage.getBody(), StandardCharsets.UTF_8);
        OcrJobResultMessage message;
        try {
            message = objectMapper.readValue(body, OcrJobResultMessage.class);
        } catch (JsonProcessingException ex) {
            log.warn("Ignored invalid OCR result message: {}", body, ex);
            meterRegistry.counter("arena.ocr.results.ignored", "reason", "invalid-json").increment();
            return;
        }

        if (message == null || message.getJobId() == null || message.getJobId().isBlank()) {
            log.warn("Ignored OCR result without jobId");
            meterRegistry.counter("arena.ocr.results.ignored", "reason", "missing-job-id").increment();
            return;
        }
        asyncDraftApplicationService.handleOcrResult(message);
    }
}
