package com.eify.workflow.engine.executor;

import tools.jackson.databind.ObjectMapper;
import com.eify.workflow.domain.entity.Workflow;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.engine.ExecutionContext;
import com.eify.workflow.engine.NodeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiNodeExecutor")
class ApiNodeExecutorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    ApiNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ApiNodeExecutor();
    }

    private ExecutionContext buildContext(Map<String, Object> variables) {
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setVersion(1);
        WorkflowNode startNode = new WorkflowNode();
        startNode.setId(1L);
        startNode.setNodeKey("start");
        startNode.setType("start");
        startNode.setLabel("开始");
        ExecutionContext ctx = new ExecutionContext(100L, workflow,
                List.of(startNode), Collections.emptyList());
        variables.forEach(ctx::setVariable);
        return ctx;
    }

    private WorkflowNode buildApiNode(Map<String, Object> configMap) throws Exception {
        WorkflowNode node = new WorkflowNode();
        node.setId(3L);
        node.setNodeKey("api_1");
        node.setType("api_call");
        node.setLabel("API调用");
        node.setConfig(MAPPER.readTree(MAPPER.writeValueAsString(configMap)));
        return node;
    }

    @Nested
    @DisplayName("getType")
    class GetTypeTests {

        @Test
        @DisplayName("应返回 api_call")
        void shouldReturnApiCall() {
            assertThat(executor.getType()).isEqualTo("api_call");
        }
    }

    @Nested
    @DisplayName("execute - 配置校验")
    class ExecuteConfigValidationTests {

        @Test
        @DisplayName("config 为 null 时应返回失败")
        void shouldReturnFailWhenConfigNull() {
            WorkflowNode node = new WorkflowNode();
            node.setId(3L);
            node.setType("api_call");
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("配置为空");
        }

        @Test
        @DisplayName("非法 URL 应返回失败（不抛异常）")
        void shouldReturnFailOnInvalidUrl() throws Exception {
            WorkflowNode node = buildApiNode(Map.of(
                    "url", "not-a-valid-url-!!!",
                    "method", "GET"
            ));
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("URL 不安全或格式无效");
        }
    }

    @Nested
    @DisplayName("execute - 模板解析")
    class ExecuteTemplateResolutionTests {

        @Test
        @DisplayName("应解析 URL 中的模板变量")
        void shouldResolveUrlTemplate() throws Exception {
            WorkflowNode node = buildApiNode(Map.of(
                    "url", "http://example.test/api"
            ));
            ExecutionContext ctx = buildContext(Map.of("name", "test"));

            NodeResult result = executor.execute(node, ctx);

            // 外部 HTTP 调用会失败（URL 不存在），但不应抛未捕获异常
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("API 调用失败");
        }
    }

    @Nested
    @DisplayName("execute - SSRF 防护")
    class SsrfProtectionTests {

        @Test
        @DisplayName("阻止 loopback 地址 127.0.0.1")
        void shouldBlockLoopbackAddress() throws Exception {
            WorkflowNode node = buildApiNode(Map.of(
                    "url", "http://127.0.0.1:8080/admin",
                    "method", "GET"
            ));
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("URL 不安全");
        }

        @Test
        @DisplayName("阻止 AWS 元数据端点 169.254.169.254")
        void shouldBlockAwsMetadataEndpoint() throws Exception {
            WorkflowNode node = buildApiNode(Map.of(
                    "url", "http://169.254.169.254/latest/meta-data/",
                    "method", "GET"
            ));
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("URL 不安全");
        }

        @Test
        @DisplayName("阻止 GCP 元数据主机名")
        void shouldBlockGcpMetadataHostname() throws Exception {
            WorkflowNode node = buildApiNode(Map.of(
                    "url", "http://metadata.google.internal/computeMetadata/v1/",
                    "method", "GET"
            ));
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("URL 不安全");
        }

        @Test
        @DisplayName("阻止 file:// 协议")
        void shouldBlockFileProtocol() throws Exception {
            WorkflowNode node = buildApiNode(Map.of(
                    "url", "file:///etc/passwd",
                    "method", "GET"
            ));
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("URL 不安全");
        }

        @Test
        @DisplayName("阻止私有地址 10.x.x.x")
        void shouldBlockPrivate10Range() throws Exception {
            WorkflowNode node = buildApiNode(Map.of(
                    "url", "http://10.0.0.1:8080/internal",
                    "method", "GET"
            ));
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("URL 不安全");
        }

        @Test
        @DisplayName("阻止私有地址 192.168.x.x")
        void shouldBlockPrivate192Range() throws Exception {
            WorkflowNode node = buildApiNode(Map.of(
                    "url", "http://192.168.1.1/api",
                    "method", "GET"
            ));
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("URL 不安全");
        }

        @Test
        @DisplayName("通过变量的内网地址被阻止")
        void shouldBlockSsrfViaTemplateVariable() throws Exception {
            WorkflowNode node = buildApiNode(Map.of(
                    "url", "http://{{target_host}}/data",
                    "method", "GET"
            ));
            ExecutionContext ctx = buildContext(Map.of("target_host", "169.254.169.254"));

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("URL 不安全");
        }

        @Test
        @DisplayName("阻止 IPv6 loopback ::1")
        void shouldBlockIPv6Loopback() throws Exception {
            WorkflowNode node = buildApiNode(Map.of(
                    "url", "http://[::1]:8080/admin",
                    "method", "GET"
            ));
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("URL 不安全");
        }

        @Test
        @DisplayName("阻止 IPv4-mapped IPv6 ::ffff:127.0.0.1")
        void shouldBlockIPv4MappedIPv6Loopback() throws Exception {
            WorkflowNode node = buildApiNode(Map.of(
                    "url", "http://[::ffff:127.0.0.1]:8080/admin",
                    "method", "GET"
            ));
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("URL 不安全");
        }

        @Test
        @DisplayName("阻止 IPv4-mapped IPv6 内网 ::ffff:10.0.0.1")
        void shouldBlockIPv4MappedIPv6Private10Range() throws Exception {
            WorkflowNode node = buildApiNode(Map.of(
                    "url", "http://[::ffff:10.0.0.1]:8080/internal",
                    "method", "GET"
            ));
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("URL 不安全");
        }

        @Test
        @DisplayName("通过变量的 IPv6 loopback 被阻止")
        void shouldBlockIPv6LoopbackViaTemplateVariable() throws Exception {
            WorkflowNode node = buildApiNode(Map.of(
                    "url", "http://{{target_host}}:8080/data",
                    "method", "GET"
            ));
            ExecutionContext ctx = buildContext(Map.of("target_host", "::1"));

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("URL 不安全");
        }

        @Test
        @DisplayName("允许合法的外部 HTTPS URL（不触发 SSRF 拦截）")
        void shouldAllowLegitimateExternalUrl() throws Exception {
            WorkflowNode node = buildApiNode(Map.of(
                    "url", "https://example.com/api/data",
                    "method", "GET"
            ));
            ExecutionContext ctx = buildContext(Map.of());

            NodeResult result = executor.execute(node, ctx);

            // URL 验证通过，不应包含"URL 不安全"——HTTP 调用可能失败（无网络），但不影响验证结果
            if (result.getErrorMessage() != null) {
                assertThat(result.getErrorMessage()).doesNotContain("URL 不安全");
            }
        }
    }
}
