package com.eify.app.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * pgvector Flyway 迁移配置
 * <p>
 * 为 PostgreSQL/pgvector 执行独立的 Flyway 迁移，与主 MySQL Flyway 隔离。
 * 迁移文件位于 {@code db/migration-pg/}，迁移历史表为 {@code flyway_schema_history_pg}。
 * <p>
 * 不暴露 Flyway Bean，避免 @ConditionalOnMissingBean 阻断主 MySQL Flyway 自动配置。
 */
@Slf4j
@Configuration
public class PgFlywayConfig {

    @Autowired
    @Qualifier("pgvectorDataSource")
    private DataSource pgDataSource;

    @jakarta.annotation.PostConstruct
    public void migrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(pgDataSource)
                .locations("classpath:db/migration-pg")
                .table("flyway_schema_history_pg")
                .baselineOnMigrate(true)
                .load();
        try {
            flyway.migrate();
            log.info("[PgFlyway] pgvector 迁移完成");
        } catch (Exception e) {
            log.warn("[PgFlyway] pgvector 迁移跳过（PG 不可用）: {}", e.getMessage());
        }
    }
}
