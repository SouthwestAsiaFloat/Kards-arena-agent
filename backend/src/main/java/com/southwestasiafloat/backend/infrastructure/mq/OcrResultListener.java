package com.southwestasiafloat.backend.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.application.service.AsyncDraftApplicationService;
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

    public OcrResultListener(AsyncDraftApplicationService asyncDraftApplicationService,
                             ObjectMapper objectMapper) {
        this.asyncDraftApplicationService = asyncDraftApplicationService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${arena.ocr.async.result-queue}")
    public void onOcrResult(Message rawMessage) throws Exception {
        String body = new String(rawMessage.getBody(), StandardCharsets.UTF_8);
        OcrJobResultMessage message = objectMapper.readValue(body, OcrJobResultMessage.class);
        if (message == null || message.getJobId() == null || message.getJobId().isBlank()) {
            log.warn("Ignored OCR result without jobId");
            return;
        }
        asyncDraftApplicationService.handleOcrResult(message);
    }
}
