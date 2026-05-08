package com.southwestasiafloat.backend.config;

/**
 * 异步 OCR 任务队列与恢复参数配置属性。
 */

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "arena.ocr.async")
public class ArenaOcrAsyncProperties {

    private String exchange = "arena.ocr";
    private String requestQueue = "arena.ocr.requests";
    private String retryQueue = "arena.ocr.requests.retry";
    private String deadQueue = "arena.ocr.requests.dead";
    private String resultQueue = "arena.ocr.results";
    private String requestRoutingKey = "ocr.request";
    private String retryRoutingKey = "ocr.request.retry";
    private String deadRoutingKey = "ocr.request.dead";
    private String resultRoutingKey = "ocr.result";
    private Duration retryDelay = Duration.ofSeconds(2);
    private String resultListenerConcurrency = "2-8";
    private String jobRedisMapName = "arena:ocr:jobs";
    private Duration jobTtl = Duration.ofHours(2);
    private Duration jobRecoverAfter = Duration.ofMinutes(10);
    private Duration jobRecoveryFixedDelay = Duration.ofMinutes(1);
    private String jobUpdateTopicName = "arena:ocr:job-updates";

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getRequestQueue() {
        return requestQueue;
    }

    public void setRequestQueue(String requestQueue) {
        this.requestQueue = requestQueue;
    }

    public String getRetryQueue() {
        return retryQueue;
    }

    public void setRetryQueue(String retryQueue) {
        this.retryQueue = retryQueue;
    }

    public String getDeadQueue() {
        return deadQueue;
    }

    public void setDeadQueue(String deadQueue) {
        this.deadQueue = deadQueue;
    }

    public String getResultQueue() {
        return resultQueue;
    }

    public void setResultQueue(String resultQueue) {
        this.resultQueue = resultQueue;
    }

    public String getRequestRoutingKey() {
        return requestRoutingKey;
    }

    public void setRequestRoutingKey(String requestRoutingKey) {
        this.requestRoutingKey = requestRoutingKey;
    }

    public String getRetryRoutingKey() {
        return retryRoutingKey;
    }

    public void setRetryRoutingKey(String retryRoutingKey) {
        this.retryRoutingKey = retryRoutingKey;
    }

    public String getDeadRoutingKey() {
        return deadRoutingKey;
    }

    public void setDeadRoutingKey(String deadRoutingKey) {
        this.deadRoutingKey = deadRoutingKey;
    }

    public String getResultRoutingKey() {
        return resultRoutingKey;
    }

    public void setResultRoutingKey(String resultRoutingKey) {
        this.resultRoutingKey = resultRoutingKey;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public String getResultListenerConcurrency() {
        return resultListenerConcurrency;
    }

    public void setResultListenerConcurrency(String resultListenerConcurrency) {
        this.resultListenerConcurrency = resultListenerConcurrency;
    }

    public String getJobRedisMapName() {
        return jobRedisMapName;
    }

    public void setJobRedisMapName(String jobRedisMapName) {
        this.jobRedisMapName = jobRedisMapName;
    }

    public Duration getJobTtl() {
        return jobTtl;
    }

    public void setJobTtl(Duration jobTtl) {
        this.jobTtl = jobTtl;
    }

    public Duration getJobRecoverAfter() {
        return jobRecoverAfter;
    }

    public void setJobRecoverAfter(Duration jobRecoverAfter) {
        this.jobRecoverAfter = jobRecoverAfter;
    }

    public Duration getJobRecoveryFixedDelay() {
        return jobRecoveryFixedDelay;
    }

    public void setJobRecoveryFixedDelay(Duration jobRecoveryFixedDelay) {
        this.jobRecoveryFixedDelay = jobRecoveryFixedDelay;
    }

    public String getJobUpdateTopicName() {
        return jobUpdateTopicName;
    }

    public void setJobUpdateTopicName(String jobUpdateTopicName) {
        this.jobUpdateTopicName = jobUpdateTopicName;
    }
}
