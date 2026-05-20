package com.eify.common.log.model;

import com.eify.common.log.AppInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一日志 Header 结构
 *
 * <p>包含 14 个标准字段：
 * <ul>
 *   <li>日期：yyyy-MM-dd</li>
 *   <li>时间：HH:mm:ss.SSS</li>
 *   <li>PID：进程 ID</li>
 *   <li>执行类：类名</li>
 *   <li>代码行号：行号</li>
 *   <li>日志等级：TRACE/DEBUG/INFO/WARN/ERROR（Java 标准日志等级）</li>
 *   <li>日志类型：REQ/SQL/MSG/SIMPLE/SYS（自定义日志类型）</li>
 *   <li>traceId：链路追踪 ID（从 Micrometer Tracing MDC 读取）</li>
 *   <li>spanId：跨度 ID（从 Micrometer Tracing MDC 读取）</li>
 *   <li>应用名称：从配置读取</li>
 *   <li>IP：服务器 IP</li>
 *   <li>版本：应用版本</li>
 *   <li>pod：Pod 名称（从环境变量读取）</li>
 *   <li>请求处理时间：毫秒</li>
 * </ul>
 *
 * @author Claude
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogHeader {

    /**
     * 日期：yyyy-MM-dd
     */
    @Builder.Default
    private String date = LocalDate.now(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);

    /**
     * 时间：HH:mm:ss.SSS
     */
    @Builder.Default
    private String time = LocalTime.now(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));

    /**
     * 进程 ID
     */
    @Builder.Default
    private String pid = AppInfo.getPid();

    /**
     * 执行类名
     */
    private String className;

    /**
     * 代码行号
     */
    private String lineNumber;

    /**
     * 日志等级（Java 标准日志等级）
     */
    @Builder.Default
    private LogLevel level = LogLevel.INFO;

    /**
     * 日志类型
     */
    private LogType logType;

    /**
     * 链路追踪 ID（从 Micrometer Tracing MDC 读取）
     */
    private String traceId;

    /**
     * 跨度 ID（从 Micrometer Tracing MDC 读取）
     */
    private String spanId;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 服务器 IP
     */
    private String ip;

    /**
     * 应用版本
     */
    private String version;

    /**
     * Pod 名称（从环境变量读取）
     */
    @Builder.Default
    private String pod = AppInfo.getPodName();

    /**
     * 请求处理时间（毫秒）
     */
    private Long duration;

    /**
     * Micrometer Tracing MDC Key - TraceId
     */
    private static final String MDC_TRACE_ID = "traceId";

    /**
     * Micrometer Tracing MDC Key - SpanId
     */
    private static final String MDC_SPAN_ID = "spanId";

    /**
     * 从 MDC 中获取 traceId
     *
     * <p>由 Brave Tracing 自动设置到 MDC
     */
    public String getEffectiveTraceId() {
        String mdcTraceId = MDC.get(MDC_TRACE_ID);
        if (mdcTraceId != null && !mdcTraceId.isEmpty()) {
            return mdcTraceId;
        }
        // 如果 MDC 中没有，返回字段中的值（兼容非 Web 请求场景）
        return traceId;
    }

    /**
     * 从 MDC 中获取 spanId
     *
     * <p>由 Brave Tracing 自动设置到 MDC
     */
    public String getEffectiveSpanId() {
        String mdcSpanId = MDC.get(MDC_SPAN_ID);

        if (mdcSpanId != null && !mdcSpanId.isEmpty()) {
            return mdcSpanId;
        }

        // 如果 MDC 中没有，返回字段中的值（兼容非 Web 请求场景）
        return spanId;
    }

    /**
     * 格式化为字符串
     *
     * <p>新格式：[日期 时间] [PID] [执行的Class] [代码行号] [日志等级] [日志类型] [traceId] [spanId] [appName@IP] [版本][pod] - [执行时间]
     *
     * <p>示例：
     * [2026-04-16 12:31:55.602] [437] [com.eify.common.log.RequestLogInterceptor] [39] [INFO] [req] [2044610983019810816] [2044610983019810816] [eify@10.0.0.1] [1.0.0-SNAPSHOT][local] - 0ms
     *
     * <p>注意：traceId 和 spanId 为空时显示 `[-]`，而非 `[N/A]`
     */
    public String format() {
        return String.format("[%s %s] [%s] [%s] [%s] [%s] [%s] [%s] [%s] [%s@%s] [%s][%s] - %sms",
                date,
                time,
                pid,
                className != null ? className : "Unknown",
                lineNumber != null ? lineNumber : "0",
                level != null ? level.getName() : "INFO",
                logType != null ? logType.getCode() : "SYS",
                getEffectiveTraceId() != null ? getEffectiveTraceId() : "-",
                getEffectiveSpanId() != null ? getEffectiveSpanId() : "-",
                appName != null ? appName : AppInfo.getAppName(),
                ip != null ? ip : AppInfo.getServerIp(),
                version != null ? version : "1.0.0",
                pod != null ? pod : "local",
                duration != null ? duration : 0);
    }
}
