package com.southwestasiafloat.backend.config;

/**
 * 分析相关配置属性，统一承载 OCR、LLM 与缓存参数。
 */

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties(prefix = "arena.analysis")
public class ArenaAnalysisProperties {

    private Duration cacheTtl = Duration.ofMinutes(10);
    private String redisMapName = "arena:draft:analysis-cache";
    private String lockKeyPrefix = "arena:draft:locks:analyze";
    private int maxConcurrentLlmCalls = 4;
    private Duration llmPermitTimeout = Duration.ofSeconds(20);
    private Duration llmTimeout = Duration.ofSeconds(60);
    private int llmMaxRetries = 1;
    private int llmCircuitBreakerFailureThreshold = 5;
    private Duration llmCircuitBreakerOpenDuration = Duration.ofSeconds(30);
    private DataSize maxUploadSize = DataSize.ofMegabytes(20);
    private int maxActiveJobs = 100;
    private int maxActiveJobsPerSession = 2;

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public String getRedisMapName() {
        return redisMapName;
    }

    public void setRedisMapName(String redisMapName) {
        this.redisMapName = redisMapName;
    }

    public String getLockKeyPrefix() {
        return lockKeyPrefix;
    }

    public void setLockKeyPrefix(String lockKeyPrefix) {
        this.lockKeyPrefix = lockKeyPrefix;
    }

    public int getMaxConcurrentLlmCalls() {
        return maxConcurrentLlmCalls;
    }

    public void setMaxConcurrentLlmCalls(int maxConcurrentLlmCalls) {
        this.maxConcurrentLlmCalls = maxConcurrentLlmCalls;
    }

    public Duration getLlmPermitTimeout() {
        return llmPermitTimeout;
    }

    public void setLlmPermitTimeout(Duration llmPermitTimeout) {
        this.llmPermitTimeout = llmPermitTimeout;
    }

    public Duration getLlmTimeout() {
        return llmTimeout;
    }

    public void setLlmTimeout(Duration llmTimeout) {
        this.llmTimeout = llmTimeout;
    }

    public int getLlmMaxRetries() {
        return llmMaxRetries;
    }

    public void setLlmMaxRetries(int llmMaxRetries) {
        this.llmMaxRetries = llmMaxRetries;
    }

    public int getLlmCircuitBreakerFailureThreshold() {
        return llmCircuitBreakerFailureThreshold;
    }

    public void setLlmCircuitBreakerFailureThreshold(int llmCircuitBreakerFailureThreshold) {
        this.llmCircuitBreakerFailureThreshold = llmCircuitBreakerFailureThreshold;
    }

    public Duration getLlmCircuitBreakerOpenDuration() {
        return llmCircuitBreakerOpenDuration;
    }

    public void setLlmCircuitBreakerOpenDuration(Duration llmCircuitBreakerOpenDuration) {
        this.llmCircuitBreakerOpenDuration = llmCircuitBreakerOpenDuration;
    }

    public DataSize getMaxUploadSize() {
        return maxUploadSize;
    }

    public void setMaxUploadSize(DataSize maxUploadSize) {
        this.maxUploadSize = maxUploadSize;
    }

    public int getMaxActiveJobs() {
        return maxActiveJobs;
    }

    public void setMaxActiveJobs(int maxActiveJobs) {
        this.maxActiveJobs = maxActiveJobs;
    }

    public int getMaxActiveJobsPerSession() {
        return maxActiveJobsPerSession;
    }

    public void setMaxActiveJobsPerSession(int maxActiveJobsPerSession) {
        this.maxActiveJobsPerSession = maxActiveJobsPerSession;
    }
}
