package com.southwestasiafloat.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "arena.analysis")
public class ArenaAnalysisProperties {

    private Duration cacheTtl = Duration.ofMinutes(10);
    private String redisMapName = "arena:draft:analysis-cache";
    private String lockKeyPrefix = "arena:draft:locks:analyze";

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
}
