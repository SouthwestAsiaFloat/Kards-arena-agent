package com.southwestasiafloat.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arena.ocr.async")
public class ArenaOcrAsyncProperties {

    private String exchange = "arena.ocr";
    private String requestQueue = "arena.ocr.requests";
    private String resultQueue = "arena.ocr.results";
    private String requestRoutingKey = "ocr.request";
    private String resultRoutingKey = "ocr.result";

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

    public String getResultRoutingKey() {
        return resultRoutingKey;
    }

    public void setResultRoutingKey(String resultRoutingKey) {
        this.resultRoutingKey = resultRoutingKey;
    }
}
