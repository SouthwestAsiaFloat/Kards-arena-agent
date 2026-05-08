package com.southwestasiafloat.backend.infrastructure.websocket;

/**
 * 分析任务 WebSocket 处理器。
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.application.analysis.support.AnalyzeJobStore;
import com.southwestasiafloat.backend.application.analysis.support.AnalyzeJobUpdateNotifier;
import com.southwestasiafloat.backend.application.analysis.model.AsyncDraftAnalyzeJob;
import com.southwestasiafloat.backend.config.ArenaOcrAsyncProperties;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeJobStatusResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class AnalyzeJobWebSocketHandler extends TextWebSocketHandler implements AnalyzeJobUpdateNotifier {

    private final ObjectMapper objectMapper;
    private final AnalyzeJobStore analyzeJobStore;
    private final ObjectProvider<RedissonClient> redissonClientProvider;
    private final ArenaOcrAsyncProperties ocrAsyncProperties;
    private final ConcurrentMap<String, Set<WebSocketSession>> sessionsByJobId = new ConcurrentHashMap<>();
    private RTopic jobUpdateTopic;

    public AnalyzeJobWebSocketHandler(ObjectMapper objectMapper,
                                      AnalyzeJobStore analyzeJobStore,
                                      ObjectProvider<RedissonClient> redissonClientProvider,
                                      ArenaOcrAsyncProperties ocrAsyncProperties) {
        this.objectMapper = objectMapper;
        this.analyzeJobStore = analyzeJobStore;
        this.redissonClientProvider = redissonClientProvider;
        this.ocrAsyncProperties = ocrAsyncProperties;
    }

    @PostConstruct
    public void subscribeRedisUpdates() {
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient == null) {
            return;
        }

        this.jobUpdateTopic = redissonClient.getTopic(ocrAsyncProperties.getJobUpdateTopicName());
        this.jobUpdateTopic.addListener(String.class, (channel, payload) -> {
            try {
                DraftAnalyzeJobStatusResponse response =
                        objectMapper.readValue(payload, DraftAnalyzeJobStatusResponse.class);
                sendPayload(response.getJobId(), payload);
            } catch (Exception ex) {
                log.warn("Ignored invalid analyze job pub/sub update: {}", payload, ex);
            }
        });
        log.info("Subscribed analyze job websocket updates on Redis topic {}",
                ocrAsyncProperties.getJobUpdateTopicName());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String jobId = extractJobId(session.getUri());
        if (jobId == null || jobId.isBlank()) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        session.getAttributes().put("jobId", jobId);
        sessionsByJobId.computeIfAbsent(jobId, ignored -> ConcurrentHashMap.newKeySet()).add(session);

        analyzeJobStore.findById(jobId)
                .ifPresent(job -> sendSnapshot(session, job));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object jobIdAttribute = session.getAttributes().get("jobId");
        if (!(jobIdAttribute instanceof String jobId)) {
            return;
        }

        Set<WebSocketSession> sessions = sessionsByJobId.get(jobId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByJobId.remove(jobId);
            }
        }
    }

    @Override
    public void notifyJob(AsyncDraftAnalyzeJob job) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(DraftAnalyzeJobStatusResponse.from(job));
        } catch (Exception ex) {
            log.warn("Failed to serialize analyze job update for {}", job.getJobId(), ex);
            return;
        }

        if (jobUpdateTopic != null) {
            try {
                jobUpdateTopic.publish(payload);
                return;
            } catch (Exception ex) {
                log.warn("Failed to publish analyze job update to Redis topic, falling back to local websocket", ex);
            }
        }

        sendPayload(job.getJobId(), payload);
    }

    private void sendPayload(String jobId, String payload) {
        Set<WebSocketSession> sessions = sessionsByJobId.get(jobId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (Exception ex) {
                log.warn("Failed to send analyze job update to websocket session {}", session.getId(), ex);
            }
        }
    }

    private void sendSnapshot(WebSocketSession session, AsyncDraftAnalyzeJob job) {
        try {
            String payload = objectMapper.writeValueAsString(DraftAnalyzeJobStatusResponse.from(job));
            session.sendMessage(new TextMessage(payload));
        } catch (Exception ex) {
            log.warn("Failed to send initial analyze job snapshot to websocket session {}", session.getId(), ex);
        }
    }

    private String extractJobId(URI uri) {
        if (uri == null || uri.getPath() == null) {
            return null;
        }

        String path = uri.getPath();
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex + 1 >= path.length()) {
            return null;
        }
        return path.substring(slashIndex + 1);
    }
}
