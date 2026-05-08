package com.southwestasiafloat.backend.config;

/**
 * Rate limit configuration for user-facing analyze submission endpoints.
 */

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "arena.rate-limit.analyze")
public class ArenaRateLimitProperties {

    private boolean enabled = true;
    private String keyPrefix = "arena:rate-limit:analyze";
    private Duration bucketTtl = Duration.ofMinutes(10);

    private boolean perSessionEnabled = true;
    private int perSessionCapacity = 10;
    private int perSessionRefillTokens = 10;
    private Duration perSessionRefillPeriod = Duration.ofMinutes(1);

    private boolean perIpEnabled = true;
    private int perIpCapacity = 30;
    private int perIpRefillTokens = 30;
    private Duration perIpRefillPeriod = Duration.ofMinutes(1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getBucketTtl() {
        return bucketTtl;
    }

    public void setBucketTtl(Duration bucketTtl) {
        this.bucketTtl = bucketTtl;
    }

    public boolean isPerSessionEnabled() {
        return perSessionEnabled;
    }

    public void setPerSessionEnabled(boolean perSessionEnabled) {
        this.perSessionEnabled = perSessionEnabled;
    }

    public int getPerSessionCapacity() {
        return perSessionCapacity;
    }

    public void setPerSessionCapacity(int perSessionCapacity) {
        this.perSessionCapacity = perSessionCapacity;
    }

    public int getPerSessionRefillTokens() {
        return perSessionRefillTokens;
    }

    public void setPerSessionRefillTokens(int perSessionRefillTokens) {
        this.perSessionRefillTokens = perSessionRefillTokens;
    }

    public Duration getPerSessionRefillPeriod() {
        return perSessionRefillPeriod;
    }

    public void setPerSessionRefillPeriod(Duration perSessionRefillPeriod) {
        this.perSessionRefillPeriod = perSessionRefillPeriod;
    }

    public boolean isPerIpEnabled() {
        return perIpEnabled;
    }

    public void setPerIpEnabled(boolean perIpEnabled) {
        this.perIpEnabled = perIpEnabled;
    }

    public int getPerIpCapacity() {
        return perIpCapacity;
    }

    public void setPerIpCapacity(int perIpCapacity) {
        this.perIpCapacity = perIpCapacity;
    }

    public int getPerIpRefillTokens() {
        return perIpRefillTokens;
    }

    public void setPerIpRefillTokens(int perIpRefillTokens) {
        this.perIpRefillTokens = perIpRefillTokens;
    }

    public Duration getPerIpRefillPeriod() {
        return perIpRefillPeriod;
    }

    public void setPerIpRefillPeriod(Duration perIpRefillPeriod) {
        this.perIpRefillPeriod = perIpRefillPeriod;
    }
}
