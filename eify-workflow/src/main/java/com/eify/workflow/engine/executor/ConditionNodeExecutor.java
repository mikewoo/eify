package com.eify.workflow.engine.executor;

import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.workflow.domain.config.ConditionNodeConfig;
import com.eify.workflow.domain.config.NodeConfigParser;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.engine.ExecutionContext;
import com.eify.workflow.engine.NodeExecutor;
import com.eify.workflow.engine.NodeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条件分支执行器，支持两种模式：
 * <p>
 * <b>多路路由（裸变量）：</b>{@code {{var}}} — 将变量值直接作为路由 handle，
 * 边缘 sourceHandle 需匹配变量值（如 "退货"/"换货"/"咨询"）。
 * <p>
 * <b>布尔求值（带运算符）：</b>
 * <ul>
 *   <li>{@code {{var}} == "value"} — 等于</li>
 *   <li>{@code {{var}} != "value"} — 不等于</li>
 *   <li>{@code {{var}} contains "value"} — 包含</li>
 *   <li>{@code {{var}} isEmpty} — 为空</li>
 * </ul>
 */
@Slf4j
@Component
public class ConditionNodeExecutor implements NodeExecutor {

    // 裸变量：{{var}}（精确匹配，用于多路路由）
    private static final Pattern BARE_VAR_PATTERN =
            Pattern.compile("^\\{\\{([^}]+)}}$");
    // {{var}} == "value" 或 {{var}} != "value"
    private static final Pattern COMPARE_PATTERN =
            Pattern.compile("\\{\\{(.+?)}}\\s*(==|!=)\\s*\"(.+?)\"");
    // {{var}} contains "value"
    private static final Pattern CONTAINS_PATTERN =
            Pattern.compile("\\{\\{(.+?)}}\\s+contains\\s+\"(.+?)\"");
    // {{var}} isEmpty
    private static final Pattern IS_EMPTY_PATTERN =
            Pattern.compile("\\{\\{(.+?)}}\\s+isEmpty");

    @Override
    public String getType() {
        return "condition";
    }

    @Override
    public NodeResult execute(WorkflowNode node, ExecutionContext ctx) {
        ConditionNodeConfig config = (ConditionNodeConfig) NodeConfigParser.parse("condition", node.getConfig());
        if (config == null || config.expression() == null) {
            return NodeResult.fail("条件节点缺少表达式");
        }

        String expression = config.expression().trim();

        // 多路路由：裸变量 {{var}} → 使用变量值作为 handle
        Matcher bareMatcher = BARE_VAR_PATTERN.matcher(expression);
        if (bareMatcher.find()) {
            String varName = bareMatcher.group(1);
            Object value = ctx.getVariable(varName);
            log.info("条件求值(多路): expression={}, varName={}, rawValue={}, varSnapshot={}",
                    expression, varName, value, ctx.getVariableSnapshot());
            String handle = value != null ? value.toString().trim() : "";
            if (handle.isEmpty()) {
                return NodeResult.fail("条件变量为空: " + varName);
            }
            log.debug("条件求值(多路): expression={}, handle={}", expression, handle);
            return NodeResult.ok(handle, Map.of("_conditionResult", handle));
        }

        // 布尔求值：带运算符表达式 → true/false
        boolean result = evaluate(expression, ctx);
        log.debug("条件求值(布尔): expression={}, result={}", expression, result);
        return NodeResult.ok(result ? "true" : "false", Map.of("_conditionResult", result));
    }

    private boolean evaluate(String expression, ExecutionContext ctx) {

        Matcher compareMatcher = COMPARE_PATTERN.matcher(expression);
        if (compareMatcher.find()) {
            String varName = compareMatcher.group(1);
            String op = compareMatcher.group(2);
            String expected = compareMatcher.group(3);
            Object actual = ctx.getVariable(varName);
            String actualStr = actual != null ? actual.toString() : "";
            boolean eq = actualStr.equals(expected);
            return "==".equals(op) ? eq : !eq;
        }

        Matcher containsMatcher = CONTAINS_PATTERN.matcher(expression);
        if (containsMatcher.find()) {
            String varName = containsMatcher.group(1);
            String expected = containsMatcher.group(2);
            Object actual = ctx.getVariable(varName);
            String actualStr = actual != null ? actual.toString() : "";
            return actualStr.contains(expected);
        }

        Matcher isEmptyMatcher = IS_EMPTY_PATTERN.matcher(expression);
        if (isEmptyMatcher.find()) {
            String varName = isEmptyMatcher.group(1);
            Object actual = ctx.getVariable(varName);
            return actual == null || actual.toString().isEmpty();
        }

        throw new BusinessException(ErrorCode.WORKFLOW_EXPRESSION_ERROR,
                "无法解析条件表达式: " + expression);
    }
}
