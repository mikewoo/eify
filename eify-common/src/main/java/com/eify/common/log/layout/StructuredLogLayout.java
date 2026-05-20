package com.eify.common.log.layout;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;
import tools.jackson.databind.ObjectMapper;
import com.eify.common.log.AppInfo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一日志格式 Layout
 *
 * <p>日志分类处理：
 * <ul>
 *   <li>业务日志（com.eify 包）：智能解析 JSON 字符串，输出结构化数据</li>
 *   <li>StructuredLogger 日志：保持原有类型（req/sql/msg）</li>
 *   <li>框架日志：简化输出，不包装 JSON</li>
 * </ul>
 *
 * <p>输出格式：
 * <pre>
 * [日期 时间] [PID] [类名] [行号] [日志等级] [日志类型] [traceId] [spanId] [app@IP] [版本][pod] - 耗时 | {JSON}
 * </pre>
 *
 * @author Claude
 * @since 1.0.0
 */
public class StructuredLogLayout extends LayoutBase<ILoggingEvent> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 使用 UTC 时区格式化时间戳，确保 ClickHouse 查询兼容性
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.of("UTC"));

    private static final String BUSINESS_PACKAGE = "com.eify";

    // 匹配日志中的 JSON 字符串：{...}
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[^{}]*\\}");

    /**
     * 应用版本（从 Logback 配置中读取）
     */
    private String appVersion = "1.0.0";

    /**
     * 设置应用版本（由 Logback 配置注入）
     */
    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    @Override
    public void start() {
        // 如果配置中没有设置版本号，尝试从系统属性或环境变量读取
        if (appVersion == null || appVersion.isEmpty()) {
            String sysProp = System.getProperty("app.version");
            if (sysProp != null && !sysProp.isEmpty()) {
                appVersion = sysProp;
            } else {
                String envVar = System.getenv("APP_VERSION");
                if (envVar != null && !envVar.isEmpty()) {
                    appVersion = envVar;
                } else {
                    appVersion = "1.0.0";
                }
            }
        }
        super.start();
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        try {
            // 日期时间
            String formattedTime = DATE_TIME_FORMATTER.format(
                    Instant.ofEpochMilli(event.getTimeStamp()));

            // PID
            String pid = AppInfo.getPid();

            // 类名:行号（优先从 MDC 读取，用于 StructuredLogger）
            String className = event.getMDCPropertyMap().get("callerClassName");
            String lineNumberStr = event.getMDCPropertyMap().get("callerLineNumber");
            int lineNumber = 0;

            if (className == null) {
                // 从调用栈获取（用于普通 log.info）
                StackTraceElement[] callerData = event.getCallerData();
                className = "Unknown";
                if (callerData != null && callerData.length > 0) {
                    StackTraceElement ste = callerData[0];
                    className = ste.getClassName();
                    lineNumber = ste.getLineNumber();
                }
            } else {
                // 从 MDC 获取的行号
                try {
                    lineNumber = Integer.parseInt(lineNumberStr != null ? lineNumberStr : "0");
                } catch (NumberFormatException e) {
                    lineNumber = 0;
                }
            }

            // 日志类型判断与处理
            String rawMessage = event.getFormattedMessage();
            String logType;
            String bodyJson;
            Long duration = null;

            // 1. 检查是否来自 StructuredLogger（通过 MDC 标记）
            String structuredLogType = event.getMDCPropertyMap().get("_structuredLogType");
            if (structuredLogType != null) {
                logType = structuredLogType;
                // 直接使用消息体中的 JSON
                if (rawMessage.startsWith("{") && rawMessage.endsWith("}")) {
                    bodyJson = rawMessage;
                    // 提取耗时
                    duration = extractDurationFromJson(rawMessage);
                } else {
                    bodyJson = "{\"message\":\"" + escapeJson(rawMessage) + "\"}";
                }
            }
            // 2. 检查是否来自业务代码（com.eify 包）
            else if (isBusinessLog(className)) {
                logType = "simple";
                bodyJson = buildSimpleLogJsonWithJsonParsing(event, rawMessage);
            }
            // 3. 框架日志：使用简化的输出格式
            else {
                logType = "sys";
                bodyJson = buildFrameworkLogJson(event, rawMessage);
            }

            // 日志等级（Java 标准日志等级，大写）
            String level = event.getLevel().toString();

            // traceId 和 spanId
            String traceId = event.getMDCPropertyMap().get("traceId");
            String spanId = event.getMDCPropertyMap().get("spanId");

            // 应用信息
            String appName = AppInfo.getAppName();
            String serverIp = AppInfo.getServerIp();
            String pod = AppInfo.getPodName();

            // 耗时（毫秒）
            long durationValue = 0L;
            if (duration != null) {
                durationValue = duration;
            } else if ("req".equals(logType) || "sql".equals(logType)) {
                durationValue = extractDurationFromJson(bodyJson);
            }

            // ==================== 构建嵌套 JSON 格式 ====================
            // 标准字段在顶层
            Map<String, Object> logEntry = new LinkedHashMap<>();
            logEntry.put("timestamp", formattedTime);
            logEntry.put("pid", pid);
            logEntry.put("className", className);
            logEntry.put("lineNumber", lineNumber);
            logEntry.put("level", level);
            logEntry.put("logType", logType);
            logEntry.put("traceId", traceId != null ? traceId : "-");
            logEntry.put("spanId", spanId != null ? spanId : "-");
            logEntry.put("appName", appName);
            logEntry.put("serverIp", serverIp);
            logEntry.put("appVersion", appVersion);
            logEntry.put("pod", pod);
            logEntry.put("duration", durationValue);

            // 解析 body JSON
            Map<String, Object> bodyMap = null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(bodyJson, Map.class);
                bodyMap = parsed;
            } catch (Exception e) {
                // body 解析失败，使用空 map
                bodyMap = new HashMap<>();
            }

            // ==================== 按日志类型提取专用字段到顶层 ====================
            // REQ 日志专用字段
            if ("req".equals(logType)) {
                extractReqFieldsToTopLevel(logEntry, bodyMap);
            }
            // SQL 日志专用字段
            else if ("sql".equals(logType)) {
                extractSqlFieldsToTopLevel(logEntry, bodyMap);
            }
            // MSG 日志专用字段
            else if ("msg".equals(logType)) {
                extractMsgFieldsToTopLevel(logEntry, bodyMap);
            }

            // 剩余字段放入 message
            logEntry.put("message", bodyMap);

            // 异常信息（放在顶层）
            if (event.getThrowableProxy() != null) {
                Map<String, String> exception = new LinkedHashMap<>();
                exception.put("type", event.getThrowableProxy().getClassName());
                exception.put("message", event.getThrowableProxy().getMessage());

                // 添加完整堆栈跟踪
                String stackTrace = extractStackTrace(event.getThrowableProxy());
                if (stackTrace != null && !stackTrace.isEmpty()) {
                    exception.put("stackTrace", stackTrace);
                }

                logEntry.put("exception", exception);
            }

            return OBJECT_MAPPER.writeValueAsString(logEntry) + "\n";

        } catch (Exception e) {
            // 错误处理，返回简单格式
            return "[ERROR] Failed to format log: " + e.getMessage() + "\n";
        }
    }

    /**
     * 判断是否来自业务代码
     */
    private boolean isBusinessLog(String className) {
        if (className == null) {
            return false;
        }
        return className.startsWith(BUSINESS_PACKAGE);
    }

    /**
     * 为业务日志构建 JSON，智能解析消息中的 JSON 字符串
     *
     * 示例：
     * 输入：log.info("demo item: {}", JSONUtil.toJsonStr(entity))
     * 消息：demo item: {"name":"test","id":1}
     * 输出：{"message":"demo item:","data":{"name":"test","id":1}}
     */
    private String buildSimpleLogJsonWithJsonParsing(ILoggingEvent event, String rawMessage) {
        try {
            Map<String, Object> logData = new HashMap<>();

            // 尝试解析消息中的 JSON 字符串
            ParsedMessage parsed = parseMessageWithJson(rawMessage);

            if (!parsed.textPart.isEmpty()) {
                logData.put("message", parsed.textPart);
            }

            // 如果有 JSON 数据，添加到 logData 中
            if (parsed.jsonData != null && !parsed.jsonData.isEmpty()) {
                if (parsed.jsonData.size() == 1) {
                    logData.putAll(parsed.jsonData);
                } else {
                    logData.put("data", parsed.jsonData);
                }
            }

            return OBJECT_MAPPER.writeValueAsString(logData);
        } catch (Exception e) {
            // 解析失败，返回简单格式
            return "{\"message\":\"" + escapeJson(rawMessage) + "\"}";
        }
    }

    /**
     * 解析消息，提取文本部分和 JSON 数据
     *
     * 示例：
     * 输入：demo item: {"name":"test","id":1}
     * 输出：textPart="demo item:", jsonData={"name":"test","id":1}
     */
    private ParsedMessage parseMessageWithJson(String message) {
        ParsedMessage result = new ParsedMessage();

        if (message == null || message.isEmpty()) {
            result.textPart = "";
            return result;
        }

        // 查找最后一个 JSON 字符串
        Matcher matcher = JSON_PATTERN.matcher(message);
        String lastJson = null;
        int lastJsonEnd = 0;

        while (matcher.find()) {
            lastJson = matcher.group();
            lastJsonEnd = matcher.end();
        }

        if (lastJson != null) {
            // 有 JSON 字符串
            result.textPart = message.substring(0, message.indexOf(lastJson)).trim();

            // 尝试解析 JSON
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> jsonMap = OBJECT_MAPPER.readValue(lastJson, Map.class);
                // 如果 JSON 有多个字段，直接展开；如果只有一个字段，用 data 包装
                if (jsonMap.size() == 1) {
                    result.jsonData = jsonMap;
                } else {
                    // 多个字段，用 data 包装
                    Map<String, Object> dataWrapper = new HashMap<>();
                    dataWrapper.put("data", jsonMap);
                    result.jsonData = dataWrapper;
                }
            } catch (Exception e) {
                // JSON 解析失败，当作普通文本
                result.textPart = message;
            }
        } else {
            // 没有 JSON 字符串
            result.textPart = message;
        }

        return result;
    }

    /**
     * 解析结果
     */
    private static class ParsedMessage {
        String textPart = "";
        Map<String, Object> jsonData = null;
    }

    /**
     * 为框架日志构建 JSON
     */
    private String buildFrameworkLogJson(ILoggingEvent event, String rawMessage) {
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("message", rawMessage);
            logData.put("logger", event.getLoggerName());
            logData.put("thread", event.getThreadName());

            return OBJECT_MAPPER.writeValueAsString(logData);
        } catch (Exception e) {
            // JSON 序列化失败，返回简单格式
            return "{\"message\":\"" + escapeJson(rawMessage) + "\"}";
        }
    }

    /**
     * 转义 JSON 字符串
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * 从 JSON 中提取耗时
     */
    private long extractDurationFromJson(String json) {
        if (json == null) {
            return 0;
        }
        // 匹配 "duration":123 或 "duration":123.456
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"duration\"\\s*:\\s*(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 提取 REQ 日志专用字段到顶层
     * <p>
     * 将 requestBody, requestBodySize, response, responseSize, error 等字段
     * 从 message 复制到顶层，方便 ClickHouse 查询（message 中保留原字段）
     */
    private void extractReqFieldsToTopLevel(Map<String, Object> logEntry, Map<String, Object> bodyMap) {
        // REQ 专用字段列表（与 ClickHouse schema 字段名一致）
        String[] reqFields = {
            "clientIp", "method", "path", "status", "userAgent",
            "requestBody", "requestBodySize", "response", "responseSize",
            "error", "asyncRequest", "asyncType", "asyncCompletionStatus"
        };

        for (String field : reqFields) {
            Object value = bodyMap.get(field);
            if (value != null && !logEntry.containsKey(field)) {
                // 特殊处理：responseBody -> response
                if ("responseBody".equals(field)) {
                    logEntry.put("response", value);
                } else {
                    logEntry.put(field, value);
                }
            }
        }

        // 兼容：如果顶层已经有 responseBody，重命名为 response
        Object responseBodyValue = bodyMap.get("responseBody");
        if (responseBodyValue != null && !logEntry.containsKey("response")) {
            logEntry.put("response", responseBodyValue);
        }
    }

    /**
     * 提取 SQL 日志专用字段到顶层
     * <p>从 message 复制到顶层（message 中保留原字段）
     */
    private void extractSqlFieldsToTopLevel(Map<String, Object> logEntry, Map<String, Object> bodyMap) {
        // SQL 专用字段列表
        String[] sqlFields = {
            "appName", "sql", "params", "executionTime", "mappedId",
            "isSlowQuery", "rowCount", "error", "errorStack"
        };

        for (String field : sqlFields) {
            Object value = bodyMap.get(field);
            if (value != null && !logEntry.containsKey(field)) {
                logEntry.put(field, value);
            }
        }
    }

    /**
     * 提取 MSG 日志专用字段到顶层
     * <p>从 message 复制到顶层（message 中保留原字段）
     */
    private void extractMsgFieldsToTopLevel(Map<String, Object> logEntry, Map<String, Object> bodyMap) {
        // MSG 专用字段列表
        String[] msgFields = {
            "msgType", "operationType", "topic", "partition", "offset",
            "key", "consumerGroupId", "processResult", "error", "errorStack",
            "retryCount", "producerTime", "consumerTime", "payloadSize"
        };

        for (String field : msgFields) {
            Object value = bodyMap.get(field);
            if (value != null && !logEntry.containsKey(field)) {
                logEntry.put(field, value);
            }
        }
    }

    /**
     * 从 ThrowableProxy 提取完整堆栈跟踪
     * <p>
     * 递归处理所有原因异常，包含完整调用链
     */
    private String extractStackTrace(ch.qos.logback.classic.spi.IThrowableProxy throwableProxy) {
        if (throwableProxy == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        appendStackTrace(sb, throwableProxy, "");

        return sb.toString();
    }

    /**
     * 递归追加堆栈跟踪
     */
    private void appendStackTrace(StringBuilder sb, ch.qos.logback.classic.spi.IThrowableProxy throwableProxy, String prefix) {
        // 异常类型和消息
        sb.append(prefix).append(throwableProxy.getClassName());
        if (throwableProxy.getMessage() != null) {
            sb.append(": ").append(throwableProxy.getMessage());
        }
        sb.append("\n");

        // 堆栈跟踪元素
        ch.qos.logback.classic.spi.StackTraceElementProxy[] stackTraceElementProxies = throwableProxy.getStackTraceElementProxyArray();
        if (stackTraceElementProxies != null) {
            for (ch.qos.logback.classic.spi.StackTraceElementProxy step : stackTraceElementProxies) {
                sb.append("\tat ").append(step.getStackTraceElement()).append("\n");
            }
        }

        // 处理原因异常（递归）
        ch.qos.logback.classic.spi.IThrowableProxy cause = throwableProxy.getCause();
        if (cause != null && !cause.equals(throwableProxy)) {
            sb.append("Caused by: ");
            appendStackTrace(sb, cause, "");
        }

        // 处理抑制异常
        ch.qos.logback.classic.spi.IThrowableProxy[] suppressed = throwableProxy.getSuppressed();
        if (suppressed != null) {
            for (ch.qos.logback.classic.spi.IThrowableProxy supp : suppressed) {
                sb.append("Suppressed: ");
                appendStackTrace(sb, supp, "");
            }
        }
    }
}
