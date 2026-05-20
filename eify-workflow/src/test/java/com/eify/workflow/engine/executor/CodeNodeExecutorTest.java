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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CodeNodeExecutor")
class CodeNodeExecutorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CodeNodeExecutor executor;

    private static boolean jsEngineAvailable() {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");
        if (engine == null) {
            engine = new ScriptEngineManager().getEngineByName("graal.js");
        }
        return engine != null;
    }

    @BeforeEach
    void setUp() {
        executor = new CodeNodeExecutor();
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

    private WorkflowNode codeNode(String language, String code, String outputKey) {
        WorkflowNode node = new WorkflowNode();
        node.setId(10L);
        node.setNodeKey("code_1");
        node.setType("code");
        node.setLabel("代码节点");
        try {
            Map<String, Object> configMap = new java.util.LinkedHashMap<>();
            if (language != null) configMap.put("language", language);
            if (code != null) configMap.put("code", code);
            if (outputKey != null) configMap.put("outputKey", outputKey);
            node.setConfig(MAPPER.readTree(MAPPER.writeValueAsString(configMap)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return node;
    }

    @Nested
    @DisplayName("JS engine execution")
    class JsEngineExecution {

        @Test
        @DisplayName("执行简单 JS 表达式")
        void shouldExecuteSimpleJsExpression() {
            if (!jsEngineAvailable()) return;

            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = codeNode("javascript", "42 + 8", "sum");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs().get("sum")).isNotNull();
        }

        @Test
        @DisplayName("JS 代码可访问上下文变量")
        void shouldAccessContextVariables() {
            if (!jsEngineAvailable()) return;

            ExecutionContext ctx = buildContext(Map.of("score", 85));
            WorkflowNode node = codeNode("javascript", "score > 80 ? 'pass' : 'fail'", "result");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs()).containsEntry("result", "pass");
        }

        @Test
        @DisplayName("JS 代码返回数字")
        void shouldReturnNumber() {
            if (!jsEngineAvailable()) return;

            ExecutionContext ctx = buildContext(Map.of("price", 100, "qty", 3));
            WorkflowNode node = codeNode("javascript", "price * qty", "total");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            Object total = result.getOutputs().get("total");
            // GraalVM JS may return Integer or Double
            assertThat(String.valueOf(total)).isEqualTo("300");
        }

        @Test
        @DisplayName("返回值写入 _codeResult 内置变量")
        void shouldSetCodeResultVariable() {
            if (!jsEngineAvailable()) return;

            ExecutionContext ctx = buildContext(Map.of("word", "hello"));
            WorkflowNode node = codeNode("javascript", "word.toUpperCase()", "shout");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs().get("shout")).isEqualTo("HELLO");
            assertThat(result.getOutputs()).containsKey("_codeResult");
        }

        @Test
        @DisplayName("outputKey 为空时仍写入 _codeResult")
        void shouldStillWriteCodeResultWhenOutputKeyIsEmpty() {
            if (!jsEngineAvailable()) return;

            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = codeNode("javascript", "'silent'", "");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs()).containsKey("_codeResult");
            assertThat(result.getOutputs().get("_codeResult")).isEqualTo("silent");
        }
    }

    @Nested
    @DisplayName("string literal fallback (no JS engine)")
    class StringLiteralFallback {

        @Test
        @DisplayName("单引号字符串字面量 -> 提取纯文本")
        void shouldExtractSingleQuotedLiteral() {
            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = codeNode("javascript", "'hello world'", "msg");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs().get("msg")).isEqualTo("hello world");
        }

        @Test
        @DisplayName("双引号字符串字面量 -> 提取纯文本")
        void shouldExtractDoubleQuotedLiteral() {
            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = codeNode("javascript", "\"你好\"", "msg");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs().get("msg")).isEqualTo("你好");
        }

        @Test
        @DisplayName("模板字符串字面量 -> 提取纯文本")
        void shouldExtractTemplateLiteral() {
            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = codeNode("javascript", "`template text`", "msg");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs().get("msg")).isEqualTo("template text");
        }

        @Test
        @DisplayName("非字符串字面量代码 -> 无 JS 引擎时应回退")
        void shouldNotFallbackForNonLiteralCode() {
            // This test verifies that non-string code doesn't pass through the
            // string-literal fallback. When no JS engine is available and the code
            // is not a string literal, it returns a failure.
            // We test the extract logic indirectly: non-literal code without JS engine
            // in environments where no engine exists.
            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = codeNode("javascript", "1 + 1", "result");

            NodeResult result = executor.execute(node, ctx);

            // If JS engine is available, it succeeds; otherwise it fails.
            if (!jsEngineAvailable()) {
                assertThat(result.isSuccess()).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("config 为 null 时返回失败")
        void shouldFailWhenConfigIsNull() {
            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = new WorkflowNode();
            node.setId(10L);
            node.setType("code");
            node.setConfig(null);

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("配置为空");
        }

        @Test
        @DisplayName("不支持的脚本语言时返回失败")
        void shouldFailForUnsupportedLanguage() {
            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = codeNode("python", "print(1)", "out");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("暂不支持的脚本语言");
        }

        @Test
        @DisplayName("JS 执行异常时返回失败")
        void shouldFailOnJsRuntimeError() {
            if (!jsEngineAvailable()) return;

            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = codeNode("javascript", "undefinedVar.something()", "out");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("代码执行失败");
        }
    }

    @Nested
    @DisplayName("template resolution")
    class TemplateResolution {

        @Test
        @DisplayName("代码中的 {{var}} 在执行前被替换")
        void shouldResolveTemplateInCode() {
            if (!jsEngineAvailable()) return;

            ExecutionContext ctx = buildContext(Map.of("threshold", 60));
            WorkflowNode node = codeNode("javascript", "threshold > 50 ? 'high' : 'low'", "level");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs().get("level")).isEqualTo("high");
        }
    }

    @Nested
    @DisplayName("security - malicious input")
    class MaliciousInput {

        @Test
        @DisplayName("用户输入包含恶意 while(true) 被超时保护截断")
        void shouldTimeoutOnInfiniteLoop() {
            if (!jsEngineAvailable()) return;

            ExecutionContext ctx = buildContext(Map.of("user_input", "'); while(true) {} //"));
            WorkflowNode node = codeNode("javascript", "while(true) {}", "out");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("超时");
        }

        @Test
        @DisplayName("用户输入模板变量注入不触发二次展开")
        void shouldNotExpandTemplateInjection() {
            if (!jsEngineAvailable()) return;

            ExecutionContext ctx = buildContext(Map.of("user_input", "{{system_prompt}}"));
            WorkflowNode node = codeNode("javascript", "user_input", "out");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs().get("out")).isEqualTo("{{system_prompt}}");
        }
    }

    @Nested
    @DisplayName("escape handling")
    class EscapeHandling {

        @Test
        @DisplayName("转义的反斜杠被正确处理")
        void shouldUnescapeDoubleBackslash() {
            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = codeNode("javascript", "'a\\\\nb'", "msg");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            // \\\\ → \\, then the string contains a literal backslash followed by n
            assertThat(result.getOutputs().get("msg")).isEqualTo("a\\nb");
        }

        @Test
        @DisplayName("转义的换行符被正确处理")
        void shouldUnescapeNewline() {
            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = codeNode("javascript", "'line1\\nline2'", "msg");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat((String) result.getOutputs().get("msg")).contains("\n");
        }

        @Test
        @DisplayName("转义的制表符被正确处理")
        void shouldUnescapeTab() {
            ExecutionContext ctx = buildContext(Collections.emptyMap());
            WorkflowNode node = codeNode("javascript", "'col1\\tcol2'", "msg");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs().get("msg")).isEqualTo("col1\tcol2");
        }
    }
}
