package com.southwestasiafloat.backend.config;

/**
 * MySQL 数据源装配配置。
 */

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "arena.mysql.enabled", havingValue = "true")
public class MySqlJdbcConfig {

    @Bean(destroyMethod = "close")
    public DataSource mysqlDataSource(@Value("${MYSQL_URL:jdbc:mysql://127.0.0.1:3306/arena_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true}") String url,
                                      @Value("${MYSQL_USERNAME:arena}") String username,
                                      @Value("${MYSQL_PASSWORD:arena}") String password,
                                      @Value("${MYSQL_DRIVER_CLASS_NAME:com.mysql.cj.jdbc.Driver}") String driverClassName,
                                      ArenaMysqlProperties mysqlProperties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setPoolName("arena-mysql");
        int maximumPoolSize = Math.max(1, mysqlProperties.getMaximumPoolSize());
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(Math.min(maximumPoolSize, Math.max(0, mysqlProperties.getMinimumIdle())));
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource mysqlDataSource) {
        return new JdbcTemplate(mysqlDataSource);
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource mysqlDataSource) {
        return new DataSourceTransactionManager(mysqlDataSource);
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public ApplicationRunner mysqlSchemaInitializer(DataSource mysqlDataSource) {
        return ignored -> new ResourceDatabasePopulator(new ClassPathResource("schema-mysql.sql"))
                .execute(mysqlDataSource);
    }
}
