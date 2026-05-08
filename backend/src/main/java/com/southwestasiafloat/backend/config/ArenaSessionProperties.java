package com.southwestasiafloat.backend.config;

/**
 * 草稿会话存储与锁前缀相关配置属性。
 */

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "arena.session")
public class ArenaSessionProperties {

    private String storeType = "in-memory";
    private Duration ttl = Duration.ofHours(12);
    private String redisMapName = "arena:draft:sessions";
    private String lockKeyPrefix = "arena:draft:locks:sessions";

    public String getStoreType() {
        return storeType;
    }

    public void setStoreType(String storeType) {
        this.storeType = storeType;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
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
