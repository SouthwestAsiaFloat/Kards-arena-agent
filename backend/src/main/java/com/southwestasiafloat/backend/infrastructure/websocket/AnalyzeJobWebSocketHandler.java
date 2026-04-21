package com.southwestasiafloat.backend.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.southwestasiafloat.backend.application.service.AnalyzeJobStore;
import com.southwestasiafloat.backend.application.service.AnalyzeJobUpdateNotifier;
import com.southwestasiafloat.backend.application.service.AsyncDraftAnalyzeJob;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeJobStatusResponse;
import lombok.extern.slf4j.Slf4j;
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
    private final ConcurrentMap<String, Set<WebSocketSession>> sessionsByJobId = new ConcurrentHashMap<>();

    public AnalyzeJobWebSocketHandler(ObjectMapper objectMapper, AnalyzeJobStore analyzeJobStore) {
        this.objectMapper = objectMapper;
        this.analyzeJobStore = analyzeJobStore;
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
        Set<WebSocketSession> sessions = sessionsByJobId.get(job.getJobId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(DraftAnalyzeJobStatusResponse.from(job));
        } catch (Exception ex) {
            log.warn("Failed to serialize analyze job update for {}", job.getJobId(), ex);
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
