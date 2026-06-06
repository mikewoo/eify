package com.eify.app.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flyway 迁移配置
 * <p>
 * 为主 PostgreSQL 数据源执行 Flyway 迁移。
 * 迁移文件位于 {@code db/migration/}，迁移历史表为 {@code flyway_schema_history}。
 * <p>
 * 单数据源下采用程序化 {@code @PostConstruct} 方式执行 Flyway 迁移，
 * 迁移失败会直接中断启动。
 * 通过 {@code eify.flyway.pg.enabled=false} 可禁用（如测试环境）。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "eify.flyway.pg.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

    private final DataSource dataSource;
    private final boolean repairOnMigrate;

    public FlywayConfig(DataSource dataSource,
                        @Value("${eify.flyway.pg.repair-on-migrate:false}") boolean repairOnMigrate) {
        this.dataSource = dataSource;
        this.repairOnMigrate = repairOnMigrate;
    }

    @jakarta.annotation.PostConstruct
    public void migrate() {
        log.info("[Flyway] 开始 PostgreSQL Flyway 迁移, repairOnMigrate={}", repairOnMigrate);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .load();
        if (repairOnMigrate) {
            flyway.repair();
        }
        flyway.migrate();
        log.info("[Flyway] PostgreSQL Flyway 迁移完成");
    }
}
