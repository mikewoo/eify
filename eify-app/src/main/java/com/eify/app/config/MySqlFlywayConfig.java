package com.eify.app.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * MySQL Flyway 迁移配置
 * <p>
 * 为主 MySQL 数据源执行 Flyway 迁移，与 {@link PgFlywayConfig} 对称。
 * 迁移文件位于 {@code db/migration/}，迁移历史表为 {@code flyway_schema_history}。
 * <p>
 * 由于 PgVectorConfig 创建了 pgvectorDataSource bean，导致 Druid 自动配置和
 * Flyway 自动配置均被跳过，因此 MySQL 和 PG 的 Flyway 均采用程序化 {@code @PostConstruct} 方式执行。
 * <p>
 * MySQL 是必要依赖，迁移失败会直接中断启动（与 PG 的静默跳过不同）。
 * 通过 {@code eify.flyway.mysql.enabled=false} 可禁用（如 H2 测试环境）。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "eify.flyway.mysql.enabled", havingValue = "true", matchIfMissing = true)
public class MySqlFlywayConfig {

    private final DataSource dataSource;
    private final boolean repairOnMigrate;

    public MySqlFlywayConfig(DataSource dataSource,
                             @Value("${eify.flyway.mysql.repair-on-migrate:false}") boolean repairOnMigrate) {
        this.dataSource = dataSource;
        this.repairOnMigrate = repairOnMigrate;
    }

    @jakarta.annotation.PostConstruct
    public void migrate() {
        log.info("[MySqlFlyway] 开始 MySQL Flyway 迁移, repairOnMigrate={}", repairOnMigrate);
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
        log.info("[MySqlFlyway] MySQL Flyway 迁移完成");
    }
}
