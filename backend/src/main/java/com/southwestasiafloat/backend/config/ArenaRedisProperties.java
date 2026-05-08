package com.southwestasiafloat.backend.config;

/**
 * Redis 连接相关配置属性。
 */

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "arena.redis")
public class ArenaRedisProperties {

    private String address = "redis://127.0.0.1:6379";
    private int database = 0;
    private String username;
    private String password;
    private String clientName = "arena-agent";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration timeout = Duration.ofSeconds(3);
    private Duration lockWatchdogTimeout = Duration.ofSeconds(30);

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getLockWatchdogTimeout() {
        return lockWatchdogTimeout;
    }

    public void setLockWatchdogTimeout(Duration lockWatchdogTimeout) {
        this.lockWatchdogTimeout = lockWatchdogTimeout;
    }
}
