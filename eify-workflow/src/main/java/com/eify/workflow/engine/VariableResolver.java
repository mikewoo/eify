package com.eify.workflow.engine;

import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 运行时变量解析器，支持 {{key}} 模板替换。
 * <p>
 * 使用单次遍历替换所有占位符，防止变量值中的 {{other_key}} 被二次展开。
 */
public class VariableResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final Map<String, Object> variables = new ConcurrentHashMap<>();

    public void set(String key, Object value) {
        variables.put(key, value);
    }

    public Object get(String key) {
        return variables.get(key);
    }

    public Map<String, Object> snapshot() {
        return Map.copyOf(variables);
    }

    /** 替换字符串中的 {{key}} 为变量值，单次遍历不递归展开 */
    public String resolve(String template) {
        if (template == null || !template.contains("{{")) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object val = variables.get(key);
            String replacement = val != null ? val.toString() : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 从 workflow.variables 定义加载默认值 */
    public void loadDefaults(JsonNode variablesDef) {
        if (variablesDef != null && variablesDef.isArray()) {
            for (JsonNode def : variablesDef) {
                String key = def.path("key").asText();
                JsonNode defaultVal = def.path("defaultVal");
                if (key != null && !key.isEmpty() && def.has("defaultVal") && !defaultVal.isNull()) {
                    variables.put(key, defaultVal.asText());
                }
            }
        }
    }
}
