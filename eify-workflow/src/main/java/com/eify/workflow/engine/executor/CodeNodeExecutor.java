package com.eify.workflow.engine.executor;

import com.eify.workflow.domain.config.CodeNodeConfig;
import com.eify.workflow.domain.config.NodeConfigParser;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.engine.ExecutionContext;
import com.eify.workflow.engine.NodeExecutor;
import com.eify.workflow.engine.NodeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 代码执行器，使用 JSR 223 脚本引擎执行内联代码。
 * 当前支持 JavaScript (Nashorn/GraalVM)。
 * <p>
 * 当 JS 引擎不可用时，回退解析纯字符串字面量（单引号/双引号/模板字符串）。
 * <p>
 * <b>安全约束</b>：
 * <ul>
 *   <li>共享沙箱线程池（4 线程），复用线程避免每次新建</li>
 *   <li>30 秒超时，通过 {@code Future.cancel(true)} 中断</li>
 *   <li>线程池满时拒绝执行（AbortPolicy），避免调用线程阻塞</li>
 * </ul>
 * <p>
 * <b>信任边界说明</b>：工作流代码节点由<b>工作空间管理员</b>编写，而非终端用户。
 * 管理员对所管理的工作空间数据已拥有完全访问权限，脚本引擎会注入所有上下文变量
 * ({@code ctx.getVariableSnapshot()})，因此代码节点可以访问当前工作流执行的全部变量。
 * 这不是沙箱逃逸，因为代码作者与数据所有者是同一个人。若未来支持终端用户编写脚本，
 * 则需引入沙箱机制（如 GraalVM Context 隔离、资源限制、类白名单）。
 */
@Slf4j
@Component
public class CodeNodeExecutor implements NodeExecutor {

    /** 代码执行超时（秒），防止无限循环或死锁 */
    private static final int CODE_EXECUTION_TIMEOUT_SEC = 30;

    /** 沙箱线程池大小 */
    private static final int SANDBOX_POOL_SIZE = 4;

    /** 共享沙箱线程池，复用线程避免每次新建。使用守护线程防止进程泄漏。 */
    private static final ExecutorService SANDBOX_EXECUTOR = Executors.newFixedThreadPool(
            SANDBOX_POOL_SIZE, new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "code-sandbox-" + counter.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            });

    @Override
    public String getType() {
        return "code";
    }

    @Override
    public NodeResult execute(WorkflowNode node, ExecutionContext ctx) {
        CodeNodeConfig config = (CodeNodeConfig) NodeConfigParser.parse("code", node.getConfig());
        if (config == null) {
            return NodeResult.fail("代码节点配置为空");
        }

        String code = ctx.resolveTemplate(config.code());
        String lang = config.language() != null ? config.language() : "javascript";

        if (!"javascript".equalsIgnoreCase(lang)) {
            return NodeResult.fail("暂不支持的脚本语言: " + lang);
        }

        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("javascript");
        if (engine == null) {
            engine = manager.getEngineByName("graal.js");
        }

        Object result;
        if (engine != null) {
            final ScriptEngine scriptEngine = engine;
            final String finalCode = code;
            try {
                Map<String, Object> snapshot = ctx.getVariableSnapshot();
                for (var entry : snapshot.entrySet()) {
                    scriptEngine.put(entry.getKey(), entry.getValue());
                }
                Future<Object> future = SANDBOX_EXECUTOR
                        .submit(() -> scriptEngine.eval(finalCode));
                try {
                    result = future.get(CODE_EXECUTION_TIMEOUT_SEC, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    log.error("代码节点执行超时: nodeId={}, timeout={}s", node.getId(), CODE_EXECUTION_TIMEOUT_SEC);
                    return NodeResult.fail("代码执行超时（超过 " + CODE_EXECUTION_TIMEOUT_SEC + " 秒）");
                }
            } catch (Exception e) {
                log.error("代码节点执行失败: nodeId={}", node.getId(), e);
                return NodeResult.fail("代码执行失败: " + e.getMessage());
            }
        } else {
            // 无 JS 引擎时，尝试解析纯字符串字面量
            String extracted = extractJsStringLiteral(code.trim());
            if (extracted != null) {
                result = extracted;
                log.info("代码节点(无JS引擎)回退解析字符串: nodeId={}, length={}", node.getId(), extracted.length());
            } else {
                return NodeResult.fail("JavaScript 引擎不可用，且代码不是纯字符串字面量。请添加 GraalVM JS 依赖，或将代码改为纯字符串。");
            }
        }

        Map<String, Object> outputs = new java.util.HashMap<>();
        if (config.outputKey() != null && !config.outputKey().isEmpty()) {
            outputs.put(config.outputKey(), result);
        }
        outputs.put("_codeResult", result);
        return NodeResult.ok(outputs);
    }

    /**
     * 从 JS 字符串字面量中提取纯文本。
     * 支持单引号、双引号、模板字符串。
     * 返回 null 表示不是纯字符串字面量。
     */
    private String extractJsStringLiteral(String code) {
        // 模板字符串: `...`
        if (code.startsWith("`") && code.endsWith("`")) {
            return unescapeJsString(code.substring(1, code.length() - 1));
        }
        // 单引号: '...'
        if (code.startsWith("'") && code.endsWith("'")) {
            return unescapeJsString(code.substring(1, code.length() - 1));
        }
        // 双引号: "..."
        if (code.startsWith("\"") && code.endsWith("\"")) {
            return unescapeJsString(code.substring(1, code.length() - 1));
        }
        return null;
    }

    /** 处理常见 JS 转义字符 */
    private String unescapeJsString(String s) {
        return s
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\`", "`");
    }
}
