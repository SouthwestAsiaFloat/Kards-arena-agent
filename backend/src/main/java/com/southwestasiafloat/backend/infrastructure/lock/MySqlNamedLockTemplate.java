package com.southwestasiafloat.backend.infrastructure.lock;

/**
 * MySQL 命名锁模板，封装锁的获取与释放。
 */

import com.southwestasiafloat.backend.config.ArenaMysqlProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "arena.session.store-type", havingValue = "mysql")
public class MySqlNamedLockTemplate {

    private final DataSource dataSource;
    private final ArenaMysqlProperties mysqlProperties;
    private final ThreadLocal<Map<String, HeldLock>> heldLocks = ThreadLocal.withInitial(HashMap::new);

    public MySqlNamedLockTemplate(DataSource dataSource,
                                  ArenaMysqlProperties mysqlProperties) {
        this.dataSource = dataSource;
        this.mysqlProperties = mysqlProperties;
    }

    public <T> T withLock(String lockName, Supplier<T> action) {
        String mysqlLockName = toMysqlLockName(lockName);
        Map<String, HeldLock> threadLocks = heldLocks.get();
        HeldLock heldLock = threadLocks.get(mysqlLockName);
        if (heldLock != null) {
            heldLock.retain();
            try {
                return action.get();
            } finally {
                releaseHeldLock(mysqlLockName, heldLock, threadLocks);
            }
        }

        Connection connection = DataSourceUtils.getConnection(dataSource);
        boolean locked = false;
        try {
            locked = acquire(connection, mysqlLockName);
            if (!locked) {
                throw new IllegalStateException("Timed out waiting for MySQL lock: " + lockName);
            }
            threadLocks.put(mysqlLockName, new HeldLock(connection));
            return action.get();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to use MySQL lock: " + lockName, ex);
        } finally {
            if (locked) {
                releaseHeldLock(mysqlLockName, threadLocks.get(mysqlLockName), threadLocks);
            } else {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
            if (threadLocks.isEmpty()) {
                heldLocks.remove();
            }
        }
    }

    private boolean acquire(Connection connection, String lockName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, lockName);
            statement.setInt(2, waitSeconds());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }

    private void release(Connection connection, String lockName) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, lockName);
            try (ResultSet ignored = statement.executeQuery()) {
                // MySQL 返回 1/0/NULL，这里已经没有更多可供调用方处理的信息。
            }
        } catch (Exception ignored) {
            // 连接即将释放，此处发生的锁丢失已无法在当前层面恢复。
        }
    }

    private void releaseHeldLock(String lockName,
                                 HeldLock heldLock,
                                 Map<String, HeldLock> threadLocks) {
        if (heldLock == null) {
            return;
        }
        if (heldLock.releaseReference()) {
            threadLocks.remove(lockName);
            release(heldLock.connection(), lockName);
            DataSourceUtils.releaseConnection(heldLock.connection(), dataSource);
        }
    }

    private int waitSeconds() {
        Duration timeout = mysqlProperties.getLockWaitTimeout();
        if (timeout == null || timeout.isNegative()) {
            return 0;
        }
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, timeout.toSeconds()));
    }

    private String toMysqlLockName(String lockName) {
        if (lockName.length() <= 64) {
            return lockName;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = HexFormat.of().formatHex(digest.digest(lockName.getBytes(StandardCharsets.UTF_8)));
            return "arena:" + hash.substring(0, 58);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static final class HeldLock {
        private final Connection connection;
        private int references = 1;

        private HeldLock(Connection connection) {
            this.connection = connection;
        }

        private Connection connection() {
            return connection;
        }

        private void retain() {
            references++;
        }

        private boolean releaseReference() {
            references--;
            return references == 0;
        }
    }
}
