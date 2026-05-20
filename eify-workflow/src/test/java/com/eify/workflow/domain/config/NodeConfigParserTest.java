package com.eify.workflow.domain.config;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NodeConfigParser")
class NodeConfigParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ==================== parse ====================

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("解析 StartNodeConfig")
        void shouldParseStartNodeConfig() throws Exception {
            String jsonStr = "{\"inputVariables\": ["
                    + "{\"key\": \"user_input\", \"varType\": \"string\", \"required\": true, \"defaultVal\": \"hello\"}"
                    + "]}";
            JsonNode json = MAPPER.readTree(jsonStr);

            NodeConfig config = NodeConfigParser.parse("start", json);

            assertThat(config).isInstanceOf(StartNodeConfig.class);
            StartNodeConfig start = (StartNodeConfig) config;
            assertThat(start.type()).isEqualTo("start");
            assertThat(start.inputVariables()).hasSize(1);
            assertThat(start.inputVariables().get(0).key()).isEqualTo("user_input");
            assertThat(start.inputVariables().get(0).varType()).isEqualTo("string");
            assertThat(start.inputVariables().get(0).required()).isTrue();
            assertThat(start.inputVariables().get(0).defaultVal()).isEqualTo("hello");
        }

        @Test
        @DisplayName("解析 EndNodeConfig")
        void shouldParseEndNodeConfig() throws Exception {
            JsonNode json = MAPPER.readTree("{\"outputKey\": \"result\"}");

            NodeConfig config = NodeConfigParser.parse("end", json);

            assertThat(config).isInstanceOf(EndNodeConfig.class);
            EndNodeConfig end = (EndNodeConfig) config;
            assertThat(end.type()).isEqualTo("end");
            assertThat(end.outputKey()).isEqualTo("result");
        }

        @Test
        @DisplayName("解析 CodeNodeConfig")
        void shouldParseCodeNodeConfig() throws Exception {
            String jsonStr = "{\"language\": \"javascript\", \"code\": \"return 1+1\", \"outputKey\": \"result\"}";
            JsonNode json = MAPPER.readTree(jsonStr);

            NodeConfig config = NodeConfigParser.parse("code", json);

            assertThat(config).isInstanceOf(CodeNodeConfig.class);
            CodeNodeConfig code = (CodeNodeConfig) config;
            assertThat(code.type()).isEqualTo("code");
            assertThat(code.language()).isEqualTo("javascript");
            assertThat(code.code()).isEqualTo("return 1+1");
            assertThat(code.outputKey()).isEqualTo("result");
        }

        @Test
        @DisplayName("解析 ConditionNodeConfig")
        void shouldParseConditionNodeConfig() throws Exception {
            JsonNode json = MAPPER.readTree("{\"expression\": \"{{intent}}\"}");

            NodeConfig config = NodeConfigParser.parse("condition", json);

            assertThat(config).isInstanceOf(ConditionNodeConfig.class);
            ConditionNodeConfig cond = (ConditionNodeConfig) config;
            assertThat(cond.type()).isEqualTo("condition");
            assertThat(cond.expression()).isEqualTo("{{intent}}");
        }

        @Test
        @DisplayName("解析 LlmNodeConfig")
        void shouldParseLlmNodeConfig() throws Exception {
            String jsonStr = "{"
                    + "\"model\": \"gpt-4\", \"temperature\": 0.7, \"maxTokens\": 2000,"
                    + "\"systemPrompt\": \"You are helpful\", \"userPrompt\": \"Hello\","
                    + "\"outputKey\": \"reply\", \"providerId\": 1"
                    + "}";
            JsonNode json = MAPPER.readTree(jsonStr);

            NodeConfig config = NodeConfigParser.parse("llm", json);

            assertThat(config).isInstanceOf(LlmNodeConfig.class);
            LlmNodeConfig llm = (LlmNodeConfig) config;
            assertThat(llm.model()).isEqualTo("gpt-4");
            assertThat(llm.temperature()).isEqualTo(0.7);
            assertThat(llm.maxTokens()).isEqualTo(2000);
            assertThat(llm.systemPrompt()).isEqualTo("You are helpful");
            assertThat(llm.userPrompt()).isEqualTo("Hello");
            assertThat(llm.outputKey()).isEqualTo("reply");
            assertThat(llm.providerId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("解析 ToolCallNodeConfig")
        void shouldParseToolCallNodeConfig() throws Exception {
            String jsonStr = "{"
                    + "\"serverId\": 3, \"toolName\": \"check_refund\","
                    + "\"argumentsTemplate\": {\"orderId\": \"{{order_id}}\"},"
                    + "\"outputKey\": \"result\""
                    + "}";
            JsonNode json = MAPPER.readTree(jsonStr);

            NodeConfig config = NodeConfigParser.parse("tool_call", json);

            assertThat(config).isInstanceOf(ToolCallNodeConfig.class);
            ToolCallNodeConfig tool = (ToolCallNodeConfig) config;
            assertThat(tool.serverId()).isEqualTo(3L);
            assertThat(tool.toolName()).isEqualTo("check_refund");
            assertThat(tool.argumentsTemplate()).containsEntry("orderId", "{{order_id}}");
            assertThat(tool.outputKey()).isEqualTo("result");
        }

        @Test
        @DisplayName("解析 ApiNodeConfig")
        void shouldParseApiNodeConfig() throws Exception {
            String jsonStr = "{"
                    + "\"url\": \"https://api.example.com\", \"method\": \"POST\","
                    + "\"headers\": {\"Content-Type\": \"application/json\"},"
                    + "\"body\": {\"key\": \"value\"}, \"outputKey\": \"apiResult\","
                    + "\"timeoutSeconds\": 30"
                    + "}";
            JsonNode json = MAPPER.readTree(jsonStr);

            NodeConfig config = NodeConfigParser.parse("api_call", json);

            assertThat(config).isInstanceOf(ApiNodeConfig.class);
            ApiNodeConfig api = (ApiNodeConfig) config;
            assertThat(api.url()).isEqualTo("https://api.example.com");
            assertThat(api.method()).isEqualTo("POST");
            assertThat(api.headers()).containsEntry("Content-Type", "application/json");
            assertThat(api.outputKey()).isEqualTo("apiResult");
            assertThat(api.timeoutSeconds()).isEqualTo(30);
        }

        @Test
        @DisplayName("null JsonNode 返回 null")
        void shouldReturnNullForNullJson() {
            assertThat(NodeConfigParser.parse("start", null)).isNull();
        }

        @Test
        @DisplayName("unknown type 抛出 IllegalArgumentException")
        void shouldThrowForUnknownType() throws Exception {
            JsonNode json = MAPPER.readTree("{}");

            assertThatThrownBy(() -> NodeConfigParser.parse("unknown_type", json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown node type");
        }
    }

    // ==================== toJson ====================

    @Nested
    @DisplayName("toJson")
    class ToJson {

        @Test
        @DisplayName("序列化 StartNodeConfig 为 JsonNode")
        void shouldSerializeStartNodeConfig() {
            var def = new StartNodeConfig.VariableDef("input", "string", true, "hello");
            StartNodeConfig config = new StartNodeConfig(List.of(def));

            JsonNode json = NodeConfigParser.toJson(config);

            assertThat(json).isNotNull();
            assertThat(json.get("inputVariables")).isNotNull();
            assertThat(json.get("inputVariables").get(0).get("key").asText()).isEqualTo("input");
        }

        @Test
        @DisplayName("序列化 CodeNodeConfig 为 JsonNode")
        void shouldSerializeCodeNodeConfig() {
            CodeNodeConfig config = new CodeNodeConfig("javascript", "1+1", "out");

            JsonNode json = NodeConfigParser.toJson(config);

            assertThat(json.get("language").asText()).isEqualTo("javascript");
            assertThat(json.get("code").asText()).isEqualTo("1+1");
            assertThat(json.get("outputKey").asText()).isEqualTo("out");
        }

        @Test
        @DisplayName("序列化 ConditionNodeConfig 为 JsonNode")
        void shouldSerializeConditionNodeConfig() {
            ConditionNodeConfig config = new ConditionNodeConfig("{{intent}}");

            JsonNode json = NodeConfigParser.toJson(config);

            assertThat(json.get("expression").asText()).isEqualTo("{{intent}}");
        }

        @Test
        @DisplayName("null config 返回 null")
        void shouldReturnNullForNullConfig() {
            assertThat(NodeConfigParser.toJson(null)).isNull();
        }

        @Test
        @DisplayName("parse → toJson 往返一致性")
        void shouldRoundtripParseAndToJson() throws Exception {
            JsonNode original = MAPPER.readTree("{\"language\":\"javascript\",\"code\":\"'hi'\",\"outputKey\":\"msg\"}");

            NodeConfig config = NodeConfigParser.parse("code", original);
            JsonNode roundtripped = NodeConfigParser.toJson(config);

            assertThat(roundtripped.get("language").asText()).isEqualTo("javascript");
            assertThat(roundtripped.get("code").asText()).isEqualTo("'hi'");
            assertThat(roundtripped.get("outputKey").asText()).isEqualTo("msg");
        }
    }

    // ==================== isValidType ====================

    @Nested
    @DisplayName("isValidType")
    class IsValidType {

        @ParameterizedTest
        @CsvSource({
                "start, true",
                "end, true",
                "llm, true",
                "api_call, true",
                "condition, true",
                "code, true",
                "tool_call, true",
                "unknown, false",
                "START, false",
                "'', false"
        })
        @DisplayName("校验节点类型合法性")
        void shouldValidateNodeTypes(String type, boolean expected) {
            assertThat(NodeConfigParser.isValidType(type)).isEqualTo(expected);
        }
    }
}
