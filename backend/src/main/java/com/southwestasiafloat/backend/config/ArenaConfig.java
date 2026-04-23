package com.southwestasiafloat.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ArenaRedisProperties.class,
        ArenaSessionProperties.class,
        ArenaAnalysisProperties.class,
        ArenaOcrAsyncProperties.class
})
public class ArenaConfig {

    @Bean
    public OpenAiChatModel openAiChatModel(
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model-name:qwen3-max}") String modelName,
            @Value("${llm.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
            ArenaAnalysisProperties analysisProperties
    ) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .timeout(analysisProperties.getLlmTimeout())
                .maxRetries(Math.max(0, analysisProperties.getLlmMaxRetries()))
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "arena.session.store-type", havingValue = "redis")
    public RedissonClient redissonClient(ArenaRedisProperties redisProperties,
                                         ObjectMapper objectMapper) {
        Config config = new Config();
        config.setCodec(new JsonJacksonCodec(objectMapper));
        config.setLockWatchdogTimeout((int) redisProperties.getLockWatchdogTimeout().toMillis());

        var singleServerConfig = config.useSingleServer()
                .setAddress(redisProperties.getAddress())
                .setDatabase(redisProperties.getDatabase())
                .setConnectTimeout((int) redisProperties.getConnectTimeout().toMillis())
                .setTimeout((int) redisProperties.getTimeout().toMillis());

        if (hasText(redisProperties.getUsername())) {
            singleServerConfig.setUsername(redisProperties.getUsername());
        }
        if (hasText(redisProperties.getPassword())) {
            singleServerConfig.setPassword(redisProperties.getPassword());
        }
        if (hasText(redisProperties.getClientName())) {
            singleServerConfig.setClientName(redisProperties.getClientName());
        }

        return Redisson.create(config);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
