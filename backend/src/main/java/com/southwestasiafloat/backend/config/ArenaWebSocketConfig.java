package com.southwestasiafloat.backend.config;

/**
 * WebSocket 连接与处理器注册配置。
 */

import com.southwestasiafloat.backend.infrastructure.websocket.AnalyzeJobWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@EnableWebSocket
@Configuration
public class ArenaWebSocketConfig implements WebSocketConfigurer {

    private final AnalyzeJobWebSocketHandler analyzeJobWebSocketHandler;

    public ArenaWebSocketConfig(AnalyzeJobWebSocketHandler analyzeJobWebSocketHandler) {
        this.analyzeJobWebSocketHandler = analyzeJobWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(analyzeJobWebSocketHandler, "/ws/arena/analyze/{jobId}")
                .setAllowedOrigins("*");
    }
}
