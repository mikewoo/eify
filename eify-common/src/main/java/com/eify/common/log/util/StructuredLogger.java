package com.eify.common.log.util;

import com.eify.common.log.AppInfo;
import com.eify.common.log.AppVersion;

import com.eify.common.log.model.LogHeader;
import com.eify.common.log.model.LogLevel;
import com.eify.common.log.model.LogType;
import com.eify.common.log.message.MsgLogMessage;
import com.eify.common.log.message.ReqLogMessage;
import com.eify.common.log.message.SimpleLogMessage;
import com.eify.common.log.message.SqlLogMessage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 结构化日志工具类
 *
 * <p>提供简洁的 API 用于记录不同类型的结构化日志
 * <p>直接输出统一格式，绕过 Logback pattern 避免格式被覆盖
 *
 * @author Claude
 * @since 1.0.0
 */
@Slf4j
public class
StructuredLogger {

    /**
     * 应用名称（从 AppInfo 获取）
     */
    private static final String APP_NAME = AppInfo.getAppName();

    /**
     * 服务器 IP（从 AppInfo 获取）
     */
    private static final String SERVER_IP = AppInfo.getServerIp();

    /**
     * 记录请求日志（REQ）
     *
     * @param messageSupplier 日志消息提供者
     */
    public static void logReq(Supplier<ReqLogMessage> messageSupplier) {
        try {
            ReqLogMessage message = messageSupplier.get();
            LogHeader header = buildHeader(LogType.REQ);
            header.setDuration(message.getDuration() != null ? message.getDuration() : 0L);

            // 根据状态码设置日志等级
            Integer status = message.getStatus();
            if (status != null && status >= 400) {
                header.setLevel(LogLevel.ERROR);
            } else if (message.getDuration() != null && message.getDuration() > 1000) {
                header.setLevel(LogLevel.WARN);
            } else {
                header.setLevel(LogLevel.INFO);
            }

            message.setHeader(header);
            // 直接输出统一格式，传递logType和level
            directLog(message.format(), header.getLogType().getCode().toLowerCase(), header.getLevel());
        } catch (Exception e) {
            log.error("Failed to log REQ message", e);
        }
    }

    /**
     * 记录消息日志（MSG）
     *
     * @param messageSupplier 日志消息提供者
     */
    public static void logMsg(Supplier<MsgLogMessage> messageSupplier) {
        try {
            MsgLogMessage message = messageSupplier.get();
            // 如果 msgType 为空，设置默认值 ASYNC
            if (message.getMsgType() == null) {
                message.setMsgType(MsgLogMessage.MsgType.ASYNC);
            }
            LogHeader header = buildHeader(LogType.MSG);
            header.setDuration(message.getDuration() != null ? message.getDuration() : 0L);

            // 根据处理结果设置日志等级
            MsgLogMessage.ProcessResult result = message.getProcessResult();
            if (result == MsgLogMessage.ProcessResult.FAILED) {
                header.setLevel(LogLevel.ERROR);
            } else if (message.getDuration() != null && message.getDuration() > 1000) {
                header.setLevel(LogLevel.WARN);
            } else {
                header.setLevel(LogLevel.INFO);
            }

            message.setHeader(header);
            directLog(message.format(), header.getLogType().getCode().toLowerCase(), header.getLevel());
        } catch (Exception e) {
            log.error("Failed to log MSG message", e);
        }
    }

    /**
     * 记录 SQL 日志（SQL）
     *
     * @param messageSupplier 日志消息提供者
     */
    public static void logSql(Supplier<SqlLogMessage> messageSupplier) {
        try {
            SqlLogMessage message = messageSupplier.get();
            LogHeader header = buildHeader(LogType.SQL);

            // 根据是否慢查询或是否有错误设置日志等级
            Boolean isSlowQuery = message.getIsSlowQuery();
            String error = message.getError();

            if (error != null && !error.isEmpty()) {
                header.setLevel(LogLevel.ERROR);
            } else if (isSlowQuery != null && isSlowQuery) {
                header.setLevel(LogLevel.WARN);
            } else {
                header.setLevel(LogLevel.INFO);
            }

            message.setHeader(header);
            directLog(message.format(), header.getLogType().getCode().toLowerCase(), header.getLevel());
        } catch (Exception e) {
            log.error("Failed to log SQL message", e);
        }
    }

    /**
     * 记录普通日志（SIMPLE）
     *
     * @param message 消息内容
     * @param level   日志级别：TRACE、DEBUG、INFO、WARN、ERROR
     * @param tags    自定义标签
     */
    public static void logSimple(String message, String level, Map<String, Object> tags) {
        logSimple(() -> SimpleLogMessage.builder()
                .message(message)
                .level(level)
                .tags(tags)
                .build());
    }

    /**
     * 记录普通日志（SIMPLE）
     *
     * @param messageSupplier 日志消息提供者
     */
    public static void logSimple(Supplier<SimpleLogMessage> messageSupplier) {
        try {
            SimpleLogMessage message = messageSupplier.get();
            LogHeader header = buildHeader(LogType.SIMPLE);

            // 根据消息中的 level 字段设置日志等级
            String levelStr = message.getLevel();
            if (levelStr != null) {
                header.setLevel(LogLevel.fromName(levelStr));
            } else {
                header.setLevel(LogLevel.INFO);
            }

            message.setHeader(header);
            directLog(message.format(), header.getLogType().getCode().toLowerCase(), header.getLevel());
        } catch (Exception e) {
            log.error("Failed to log SIMPLE message", e);
        }
    }

    /**
     * 直接输出日志，通过 SLF4J 使用 StructuredLogLayout
     * 统一输出纯 JSON 格式
     *
     * @param message 日志消息
     * @param logType 日志类型
     * @param level   日志级别
     */
    private static void directLog(String message, String logType, LogLevel level) {
        // 获取调用者信息（在调用 directLog 之前）
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String callerClassName = "Unknown";
        String callerLineNumber = "0";

        // 跳过 getStackTrace、directLog、logXxx 方法
        for (int i = 1; i < stackTrace.length; i++) {
            String clazzName = stackTrace[i].getClassName();
            // 跳过 StructuredLogger 本身和 SLF4J/Logback 的调用
            if (!clazzName.equals(StructuredLogger.class.getName()) &&
                    !clazzName.startsWith("org.slf4j.") &&
                    !clazzName.startsWith("ch.qos.logback.") &&
                    !clazzName.startsWith("java.lang.Thread.")) {
                callerClassName = clazzName;
                callerLineNumber = String.valueOf(stackTrace[i].getLineNumber());
                break;
            }
        }

        // 保存原始 MDC
        Map<String, String> originalMdc = MDC.getCopyOfContextMap();

        try {
            // 设置调用者信息到 MDC
            MDC.put("callerClassName", callerClassName);
            MDC.put("callerLineNumber", callerLineNumber);
            // 设置日志类型到 MDC，供 Layout 识别
            MDC.put("_structuredLogType", logType);

            // 根据日志级别使用对应的 SLF4J 方法
            // 这样日志过滤、告警系统、日志分析工具才能正常工作
            ch.qos.logback.classic.Level logbackLevel = level.toLogbackLevel();
            if (logbackLevel.isGreaterOrEqual(ch.qos.logback.classic.Level.ERROR)) {
                log.error(message);
            } else if (logbackLevel.isGreaterOrEqual(ch.qos.logback.classic.Level.WARN)) {
                log.warn(message);
            } else if (logbackLevel.isGreaterOrEqual(ch.qos.logback.classic.Level.INFO)) {
                log.info(message);
            } else if (logbackLevel.isGreaterOrEqual(ch.qos.logback.classic.Level.DEBUG)) {
                log.debug(message);
            } else {
                log.trace(message);
            }
        } finally {
            // 恢复原始 MDC
            if (originalMdc != null) {
                MDC.setContextMap(originalMdc);
            } else {
                MDC.clear();
            }
        }
    }

    /**
     * 构建日志 Header
     *
     * @param logType 日志类型
     * @return 日志 Header
     */
    private static LogHeader buildHeader(LogType logType) {
        // 获取调用栈信息
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String className = "Unknown";
        String lineNumber = "0";

        // 跳过 getStackTrace、buildHeader 和 logXxx 方法
        for (int i = 1; i < stackTrace.length; i++) {
            String clazzName = stackTrace[i].getClassName();
            // 跳过 StructuredLogger 本身和 SLF4J 的调用
            if (!clazzName.equals(StructuredLogger.class.getName()) &&
                    !clazzName.startsWith("org.slf4j.") &&
                    !clazzName.startsWith("ch.qos.logback.")) {
                className = clazzName;
                lineNumber = String.valueOf(stackTrace[i].getLineNumber());
                break;
            }
        }

        return LogHeader.builder()
                .className(className)
                .lineNumber(lineNumber)
                .logType(logType)
                .traceId(MDC.get("traceId"))  // 从 MDC 读取（兼容 TraceIdUtils）
                .spanId(null)  // spanId 由 Micrometer Tracing 自动设置到 MDC，LogHeader 会自动读取
                .appName(APP_NAME)
                .ip(SERVER_IP)
                .version(AppVersion.get())
                .build();
    }
}
