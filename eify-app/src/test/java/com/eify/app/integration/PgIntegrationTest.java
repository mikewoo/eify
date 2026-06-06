package com.eify.app.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PG17 + pgvector 集成测试，覆盖 H2 PostgreSQL 模式测不真的方言行为：
 * JSONB ->> + ::bigint、ON CONFLICT upsert、pgvector 向量检索、IDENTITY 回填。
 * Docker 不可用时整类自动跳过（disabledWithoutDocker = true）。
 *
 * 注：Spring Boot 4.x 已移除 @AutoConfigureTestDatabase，
 * @ServiceConnection 会直接将容器连接参数注入 spring.datasource.*，无需额外配置。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("pgtest")
class PgIntegrationTest {

    @Container
    @ServiceConnection
    // PostgreSQLContainer 在 testcontainers 2.x 是自绑定泛型，不接受外部类型参数
    static PostgreSQLContainer pg = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void identitySequenceSurvivesSeedInsert() {
        // 种子 V1__init.sql 已插入 id=1 的 ai_workspace 并 setval 同步序列；
        // 新插入不应撞主键，且 id 应 > 1
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO ai_workspace (name, description) VALUES ('tc-test', 'tc') RETURNING id",
                Long.class);
        assertThat(id).isGreaterThan(1L);
    }

    @Test
    void jsonbArrowQueryWithBigintCast() {
        jdbcTemplate.update("INSERT INTO ai_workflow (id, name, status) VALUES (900, 'wf', 1) " +
                "ON CONFLICT (id) DO NOTHING");
        jdbcTemplate.update("INSERT INTO ai_workflow_node (workflow_id, node_key, type, config) " +
                "VALUES (900, 'n1', 'llm', '{\"providerId\": 42}'::jsonb)");
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_workflow_node WHERE (config->>'providerId')::bigint = 42",
                Integer.class);
        assertThat(cnt).isEqualTo(1);
    }

    @Test
    void onConflictUpsertReplacesStatus() {
        jdbcTemplate.update("INSERT INTO provider_health (provider_id, status) VALUES (5, 'UP') " +
                "ON CONFLICT (provider_id) DO UPDATE SET status = EXCLUDED.status");
        jdbcTemplate.update("INSERT INTO provider_health (provider_id, status) VALUES (5, 'DOWN') " +
                "ON CONFLICT (provider_id) DO UPDATE SET status = EXCLUDED.status");
        String s = jdbcTemplate.queryForObject(
                "SELECT status FROM provider_health WHERE provider_id = 5", String.class);
        assertThat(s).isEqualTo("DOWN");
    }

    /**
     * 构建 1024 维向量：第一维为 1.0，其余为 0。
     */
    private static String unitVector1024() {
        StringBuilder sb = new StringBuilder("[1.0");
        for (int i = 1; i < 1024; i++) {
            sb.append(",0.0");
        }
        sb.append("]");
        return sb.toString();
    }

    @Test
    void vectorCosineSearchReturnsSimilarity() {
        String v = unitVector1024();
        jdbcTemplate.update("INSERT INTO document_chunk " +
                "(knowledge_id, document_id, chunk_index, content, embedding, chunk_hash) " +
                "VALUES (1, 1, 0, 'hello', ?::vector, 'hash-tc-1')", v);
        Double sim = jdbcTemplate.queryForObject(
                "SELECT 1 - (embedding <=> ?::vector) FROM document_chunk WHERE chunk_hash = 'hash-tc-1'",
                Double.class, v);
        assertThat(sim).isGreaterThan(0.99);
    }
}
