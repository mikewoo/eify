package com.eify.knowledge.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PostgreSQL 双数据源配置
 * <p>
 * 主数据源（MySQL）由 Druid 自动配置，本类只配置 pgvector 辅助数据源。
 * MyBatis-Plus 继续走主数据源，pgvector 操作通过 JdbcTemplate 完成。
 * <p>
 * 使用 @Lazy 延迟初始化，确保 PostgreSQL 不可用时不影响其他模块启动。
 */
@Slf4j
@Configuration
public class PgVectorConfig {

    @Value("${spring.datasource-pgvector.url}")
    private String url;

    @Value("${spring.datasource-pgvector.username}")
    private String username;

    @Value("${spring.datasource-pgvector.password}")
    private String password;

    @Value("${spring.datasource-pgvector.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean("pgvectorDataSource")
    @Lazy
    public DataSource pgvectorDataSource() {
        log.info("[PgVectorConfig] 初始化 pgvector DataSource, url={}", url);
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(1);
        return dataSource;
    }

    @Bean
    @Lazy
    public JdbcTemplate pgJdbcTemplate(@Qualifier("pgvectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
