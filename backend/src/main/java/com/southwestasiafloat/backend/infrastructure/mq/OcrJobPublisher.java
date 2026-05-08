package com.southwestasiafloat.backend.infrastructure.mq;

/**
 * OCR 任务发布组件，负责把截图请求发送到消息队列。
 */

import com.southwestasiafloat.backend.config.ArenaOcrAsyncProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class OcrJobPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ArenaOcrAsyncProperties properties;

    public OcrJobPublisher(RabbitTemplate rabbitTemplate, ArenaOcrAsyncProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(String jobId, String sessionId, String filename, byte[] imageBytes) {
        OcrJobMessage message = new OcrJobMessage(
                jobId,
                sessionId,
                filename,
                Base64.getEncoder().encodeToString(imageBytes)
        );

        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getRequestRoutingKey(),
                message
        );
    }
}
