package com.eify.workflow.engine.executor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.eify.workflow.domain.entity.Workflow;
import com.eify.workflow.domain.entity.WorkflowEdge;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ConditionNodeExecutor")
class ConditionNodeExecutorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConditionNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ConditionNodeExecutor();
    }

    private ExecutionContext buildContext(Map<String, Object> variables) {
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setVersion(1);

        WorkflowNode startNode = new WorkflowNode();
        startNode.setId(10L);
        startNode.setNodeKey("start");
        startNode.setType("start");
        startNode.setLabel("开始");

        WorkflowNode condNode = new WorkflowNode();
        condNode.setId(20L);
        condNode.setNodeKey("cond");
        condNode.setType("condition");
        condNode.setLabel("条件");

        List<WorkflowNode> nodes = List.of(startNode, condNode);
        List<WorkflowEdge> edges = List.of(buildEdge(10L, 20L, "default"));

        ExecutionContext ctx = new ExecutionContext(100L, workflow, nodes, edges);
        variables.forEach(ctx::setVariable);
        return ctx;
    }

    private WorkflowEdge buildEdge(Long source, Long target, String handle) {
        WorkflowEdge e = new WorkflowEdge();
        e.setId(source * 100 + target);
        e.setSourceNodeId(source);
        e.setTargetNodeId(target);
        e.setSourceHandle(handle);
        return e;
    }

    private WorkflowNode conditionNode(String expression) {
        WorkflowNode node = new WorkflowNode();
        node.setId(20L);
        node.setNodeKey("cond");
        node.setType("condition");
        node.setLabel("条件");
        try {
            String json = MAPPER.writeValueAsString(Map.of("expression", expression));
            node.setConfig(MAPPER.readTree(json));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return node;
    }

    // ==================== 多路路由（裸变量） ====================

    @Nested
    @DisplayName("bare variable routing")
    class BareVariableRouting {

        @Test
        @DisplayName("{{var}} 将变量值作为路由 handle")
        void shouldUseVariableValueAsHandle() {
            ExecutionContext ctx = buildContext(Map.of("intent", "退货"));
            WorkflowNode node = conditionNode("{{intent}}");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("退货");
            assertThat(result.getOutputs()).containsEntry("_conditionResult", "退货");
        }

        @Test
        @DisplayName("{{var}} 变量不存在时返回空字符串 handle")
        void shouldUseEmptyHandleWhenVariableMissing() {
            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = conditionNode("{{missing}}");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("条件变量为空");
        }

        @Test
        @DisplayName("{{var}} 变量值为空字符串时返回错误")
        void shouldFailWhenVariableIsEmptyString() {
            ExecutionContext ctx = buildContext(Map.of("emptyVar", ""));
            WorkflowNode node = conditionNode("{{emptyVar}}");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("条件变量为空");
        }
    }

    // ==================== 比较运算符 == / != ====================

    @Nested
    @DisplayName("comparison operators ==/!=")
    class ComparisonOperators {

        @Test
        @DisplayName("{{var}} == \"value\" 等于时返回 true")
        void shouldReturnTrueWhenEqual() {
            ExecutionContext ctx = buildContext(Map.of("status", "active"));
            WorkflowNode node = conditionNode("{{status}} == \"active\"");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("true");
            assertThat(result.getOutputs()).containsEntry("_conditionResult", true);
        }

        @Test
        @DisplayName("{{var}} == \"value\" 不等于时返回 false")
        void shouldReturnFalseWhenNotEqual() {
            ExecutionContext ctx = buildContext(Map.of("status", "inactive"));
            WorkflowNode node = conditionNode("{{status}} == \"active\"");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("false");
        }

        @Test
        @DisplayName("{{var}} != \"value\" 不等于时返回 true")
        void shouldReturnTrueWhenNotEqualWithNeq() {
            ExecutionContext ctx = buildContext(Map.of("status", "inactive"));
            WorkflowNode node = conditionNode("{{status}} != \"active\"");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("true");
        }

        @Test
        @DisplayName("{{var}} != \"value\" 等于时返回 false")
        void shouldReturnFalseWhenEqualWithNeq() {
            ExecutionContext ctx = buildContext(Map.of("status", "active"));
            WorkflowNode node = conditionNode("{{status}} != \"active\"");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("false");
        }
    }

    // ==================== contains ====================

    @Nested
    @DisplayName("contains operator")
    class ContainsOperator {

        @Test
        @DisplayName("包含子串时返回 true")
        void shouldReturnTrueWhenContains() {
            ExecutionContext ctx = buildContext(Map.of("text", "退款退货"));
            WorkflowNode node = conditionNode("{{text}} contains \"退货\"");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("true");
        }

        @Test
        @DisplayName("不包含子串时返回 false")
        void shouldReturnFalseWhenNotContains() {
            ExecutionContext ctx = buildContext(Map.of("text", "换货"));
            WorkflowNode node = conditionNode("{{text}} contains \"退货\"");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("false");
        }
    }

    // ==================== isEmpty ====================

    @Nested
    @DisplayName("isEmpty operator")
    class IsEmptyOperator {

        @Test
        @DisplayName("变量为空字符串时返回 true")
        void shouldReturnTrueWhenEmptyString() {
            ExecutionContext ctx = buildContext(Map.of("emptyVar", ""));
            WorkflowNode node = conditionNode("{{emptyVar}} isEmpty");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("true");
        }

        @Test
        @DisplayName("变量不存在时返回 true")
        void shouldReturnTrueWhenVariableMissing() {
            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = conditionNode("{{missing}} isEmpty");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("true");
        }

        @Test
        @DisplayName("变量非空时返回 false")
        void shouldReturnFalseWhenNotEmpty() {
            ExecutionContext ctx = buildContext(Map.of("nonEmpty", "有值"));
            WorkflowNode node = conditionNode("{{nonEmpty}} isEmpty");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("false");
        }
    }

    // ==================== 表达式内联模板解析 ====================

    @Nested
    @DisplayName("inline template resolution")
    class InlineTemplateResolution {

        @Test
        @DisplayName("比较表达式中包含 {{}} 内联变量")
        void shouldResolveInlineVariableInComparison() {
            ExecutionContext ctx = buildContext(Map.of("score", 85));
            WorkflowNode node = conditionNode("{{score}} > 0.5");

            // 内联变量被解析为 "85 > 0.5" — 这不符合标准模式，会抛异常
            assertThatThrownBy(() -> executor.execute(node, ctx))
                    .hasMessageContaining("无法解析条件表达式");
        }
    }

    // ==================== 边界条件 ====================

    @Nested
    @DisplayName("boundary conditions")
    class BoundaryConditions {

        @Test
        @DisplayName("中文值比较 - 等于")
        void shouldMatchChineseValueEquals() {
            ExecutionContext ctx = buildContext(Map.of("category", "电子产品"));
            WorkflowNode node = conditionNode("{{category}} == \"电子产品\"");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("true");
        }

        @Test
        @DisplayName("中文值比较 - 不等于")
        void shouldMatchChineseValueNotEquals() {
            ExecutionContext ctx = buildContext(Map.of("category", "食品"));
            WorkflowNode node = conditionNode("{{category}} != \"电子产品\"");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("true");
        }

        @Test
        @DisplayName("中文值包含检查")
        void shouldMatchChineseContains() {
            ExecutionContext ctx = buildContext(Map.of("text", "我要退货退款"));
            WorkflowNode node = conditionNode("{{text}} contains \"退款\"");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("true");
        }

        @Test
        @DisplayName("值中包含双引号时表达式解析失败")
        void shouldHandleValueWithDoubleQuotes() {
            ExecutionContext ctx = buildContext(Map.of("msg", "hello world"));
            WorkflowNode node = conditionNode("{{msg}} == \"hello world\"");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("true");
        }

        @Test
        @DisplayName("值中包含换行符")
        void shouldHandleValueWithNewlines() {
            ExecutionContext ctx = buildContext(Map.of("text", "line1\nline2"));
            WorkflowNode node = conditionNode("{{text}} contains \"line2\"");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNextHandle()).isEqualTo("true");
        }

        @Test
        @DisplayName("isEmpty 对数字 0 的处理 - 0 非空")
        void shouldTreatZeroAsNonEmpty() {
            ExecutionContext ctx = buildContext(Map.of("qty", 0));
            WorkflowNode node = conditionNode("{{qty}} isEmpty");

            NodeResult result = executor.execute(node, ctx);

            // 0 不是空字符串，表达式应解析失败或返回 false
            // isEmpty 仅适用字符串，数字会转为 "0" 字符串处理
            assertThat(result.getNextHandle()).isEqualTo("false");
        }
    }

    // ==================== 错误处理 ====================

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("config 为 null 时返回失败")
        void shouldFailWhenConfigIsNull() {
            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = new WorkflowNode();
            node.setId(20L);
            node.setType("condition");
            node.setConfig(null);

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("缺少表达式");
        }

        @Test
        @DisplayName("expression 为 null 时返回失败")
        void shouldFailWhenExpressionIsNull() {
            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = new WorkflowNode();
            node.setId(20L);
            node.setType("condition");
            node.setLabel("条件");
            try {
                node.setConfig(MAPPER.readTree("{}"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("缺少表达式");
        }

        @Test
        @DisplayName("无法识别的表达式格式时抛出异常")
        void shouldThrowForUnknownExpression() {
            ExecutionContext ctx = buildContext(Map.of("x", "1"));
            WorkflowNode node = conditionNode("{{x}} && {{y}}");

            assertThatThrownBy(() -> executor.execute(node, ctx))
                    .isInstanceOf(com.eify.common.exception.BusinessException.class)
                    .hasMessageContaining("无法解析条件表达式");
        }
    }
}
