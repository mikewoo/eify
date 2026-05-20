package com.eify.app.config;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 主数据源配置（MySQL/Druid）
 * <p>
 * 显式创建 @Primary DataSource，确保 MyBatis-Plus 和主 Flyway 使用 MySQL。
 * 因为 PgVectorConfig 也定义了一个 DataSource bean（pgvector），
 * Druid 的 @ConditionalOnMissingBean 会因此跳过自动配置。
 */
@Slf4j
@Configuration
public class PrimaryDataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.druid")
    public DruidDataSource dataSource() {
        log.info("[PrimaryDataSourceConfig] 初始化主数据源 (MySQL/Druid)");
        return new DruidDataSource();
    }
}
