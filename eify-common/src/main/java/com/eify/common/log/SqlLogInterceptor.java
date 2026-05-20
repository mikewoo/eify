package com.eify.common.log;

import com.eify.common.log.util.StructuredLogger;
import com.eify.common.log.message.SqlLogMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

/**
 * MyBatis SQL 日志拦截器
 *
 * <p>自动记录所有 SQL 查询的日志，无需手动编写日志代码。
 *
 * <p>特性：
 * <ul>
 *   <li>自动拦截所有 MyBatis 查询</li>
 *   <li>异步日志记录，不阻塞业务</li>
 *   <li>支持采样记录，减少日志量</li>
   *   <li>自动记录参数值</li>
   *   <li>慢查询完整记录</li>
 * </ul>
 *
 * @author Claude
 * @since 1.0.0
 */
@Slf4j
@Intercepts({
        @Signature(
                type = Executor.class,
                method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}
        ),
        @Signature(
                type = Executor.class,
                method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}
        ),
        @Signature(
                type = Executor.class,
                method = "update",
                args = {MappedStatement.class, Object.class}
        )
})
public class SqlLogInterceptor implements Interceptor {

    /**
     * 异步日志记录线程池
     */
    private static final java.util.concurrent.ExecutorService LOGGING_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "sql-log-logger");
                t.setDaemon(true);
                return t;
            });

    /**
     * SQL 日志配置
     */
    private SqlLogConfig config = new SqlLogConfig();

    /**
     * 应用名称
     */
    private static final String APP_NAME = "eify";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!config.isEnabled()) {
            return invocation.proceed();
        }

        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;

        try {
            result = invocation.proceed();
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            // 异步记录日志
            long finalStartTime = startTime;
            Object finalResult = result;
            Exception finalException = exception;

            // 捕获当前线程的 MDC 上下文
            String traceId = org.slf4j.MDC.get("traceId");
            String spanId = org.slf4j.MDC.get("spanId");

            LOGGING_EXECUTOR.submit(() -> {
                try {
                    // 恢复 MDC 上下文
                    if (traceId != null) {
                        org.slf4j.MDC.put("traceId", traceId);
                    }
                    if (spanId != null) {
                        org.slf4j.MDC.put("spanId", spanId);
                    }

                    logSql(invocation, finalStartTime, executionTime, finalResult, finalException);
                } catch (Exception e) {
                    log.debug("Failed to log SQL", e);
                } finally {
                    // 清理 MDC 上下文
                    if (traceId != null) {
                        org.slf4j.MDC.remove("traceId");
                    }
                    if (spanId != null) {
                        org.slf4j.MDC.remove("spanId");
                    }
                }
            });
        }
    }

    /**
     * 记录 SQL 日志（使用 StructuredLogger）
     */
    private void logSql(Invocation invocation, long startTime, long executionTime, Object result, Exception exception) {
        try {
            // 获取 MappedStatement
            MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];

            // 获取 SQL 和参数
            SqlInfo sqlInfo = extractSqlInfo(invocation, mappedStatement);

            if (sqlInfo == null || sqlInfo.sql == null || sqlInfo.sql.isEmpty()) {
                return;
            }

            // 判断是否慢查询
            boolean isSlowQuery = executionTime > config.getSlowQueryThreshold();

            // 判断是否应该记录（采样或慢查询）
            if (!shouldLog(executionTime, isSlowQuery)) {
                return;
            }

            // 格式化 SQL
            String formattedSql = formatSql(sqlInfo.sql);

            // 获取参数摘要
            String paramsSummary = getParamsSummary(sqlInfo.parameter, isSlowQuery);

            // 构建日志消息
            SqlLogMessage.SqlLogMessageBuilder builder = SqlLogMessage.builder()
                    .timestamp(startTime)
                    .appName(APP_NAME)
                    .sql(formattedSql)
                    .executionTime(executionTime)
                    .mappedId(mappedStatement.getId())
                    .isSlowQuery(isSlowQuery);

            // 添加参数信息
            if (paramsSummary != null && !paramsSummary.isEmpty()) {
                builder.params(paramsSummary);
            }

            // 如果有结果，添加结果信息
            if (result != null) {
                builder.rowCount(getRowCount(result));
            }

            // 如果有异常，添加异常信息
            if (exception != null) {
                builder.error(exception.getMessage());
            }

            // 慢查询记录完整堆栈
            if (isSlowQuery && exception != null) {
                builder.errorStack(getStackTrace(exception));
            }

            SqlLogMessage logMessage = builder.build();
            logMessage.setExecutionTime(executionTime);

            // 使用 StructuredLogger 记录
            StructuredLogger.logSql(() -> logMessage);

        } catch (Exception e) {
            // 避免日志记录失败影响业务
            log.debug("Failed to log SQL", e);
        }
    }

    /**
     * 提取 SQL 信息
     */
    private SqlInfo extractSqlInfo(Invocation invocation, MappedStatement mappedStatement) {
        try {
            String sql = null;
            Object parameter = null;

            if (invocation.getArgs().length > 5 && invocation.getArgs()[5] instanceof BoundSql) {
                // MyBatis-Plus 分页场景
                BoundSql boundSql = (BoundSql) invocation.getArgs()[5];
                sql = boundSql.getSql();
                parameter = boundSql.getParameterObject();
            } else {
                // 普通场景
                parameter = invocation.getArgs()[1];
                BoundSql boundSql = mappedStatement.getBoundSql(parameter);
                sql = boundSql.getSql();
            }

            // 获取参数映射
            List<ParameterMapping> parameterMappings = null;
            if (parameter != null) {
                BoundSql boundSql = mappedStatement.getBoundSql(parameter);
                parameterMappings = boundSql.getParameterMappings();
            }

            return new SqlInfo(sql, parameter, parameterMappings);
        } catch (Exception e) {
            log.debug("Failed to extract SQL info", e);
            return null;
        }
    }

    /**
     * 判断是否应该记录日志
     */
    private boolean shouldLog(long executionTime, boolean isSlowQuery) {
        // 慢查询始终记录
        if (isSlowQuery) {
            return true;
        }

        // 根据采样率决定
        return Math.random() < config.getSamplingRate();
    }

    /**
     * 格式化 SQL
     */
    private String formatSql(String sql) {
        if (sql == null) {
            return "";
        }

        // 去除多余空格和换行
        String formatted = sql.replaceAll("\\s+", " ").trim();

        // 截断过长的 SQL
        if (formatted.length() > config.getMaxSqlLength()) {
            formatted = formatted.substring(0, config.getMaxSqlLength()) + "...";
        }

        return formatted;
    }

    /**
     * 获取参数摘要
     */
    private String getParamsSummary(Object parameterObject, boolean includeFullParams) {
        if (parameterObject == null) {
            return null;
        }

        // 检查是否应该记录参数
        if (!includeFullParams && !config.isRecordParams()) {
            return null;
        }

        // 慢查询或需要详细参数时
        if (includeFullParams) {
            String paramStr = parameterObject.toString();
            if (paramStr.length() > 500) {
                return paramStr.substring(0, 500) + "...";
            }
            return paramStr;
        }

        // 普通查询只记录参数类型
        if (parameterObject instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) parameterObject;
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (i > 0) sb.append(", ");
                sb.append(entry.getKey()).append(": ");
                Object value = entry.getValue();
                sb.append(value != null ? value.getClass().getSimpleName() : "null");
                i++;
            }
            sb.append("}");
            return sb.toString();
        } else if (parameterObject.getClass().isArray()) {
            Object[] arr = (Object[]) parameterObject;
            return "array[" + arr.length + "]";
        } else {
            return parameterObject.getClass().getSimpleName();
        }
    }

    /**
     * 获取结果行数
     */
    private int getRowCount(Object result) {
        if (result == null) {
            return 0;
        }

        // List 类型结果
        if (result instanceof java.util.List) {
            return ((java.util.List<?>) result).size();
        }

        // Integer/Long 类型（update 返回值）
        if (result instanceof Integer) {
            return (Integer) result;
        }

        if (result instanceof Long) {
            return ((Long) result).intValue();
        }

        // 其他类型
        return 1;
    }

    /**
     * 获取异常堆栈
     */
    private String getStackTrace(Exception exception) {
        if (exception == null) {
            return null;
        }

        if (!config.isRecordFullStack()) {
            return exception.toString();
        }

        // 记录完整堆栈，但限制深度
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);

        String stack = sw.toString();
        String[] lines = stack.split("\\n");
        if (lines.length > config.getMaxStackDepth()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < config.getMaxStackDepth(); i++) {
                sb.append(lines[i]).append("\n");
            }
            sb.append("... (").append(lines.length - config.getMaxStackDepth()).append(" more lines)");
            return sb.toString();
        }

        return stack;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        if (properties != null) {
            String slowSqlThreshold = properties.getProperty("slowSqlThresholdMillis");
            if (slowSqlThreshold != null) {
                config.setSlowQueryThreshold(Long.parseLong(slowSqlThreshold));
            }

            String logEnabled = properties.getProperty("logEnabled");
            if (logEnabled != null) {
                config.setEnabled(Boolean.parseBoolean(logEnabled));
            }

            String maxSqlLength = properties.getProperty("maxSqlLength");
            if (maxSqlLength != null) {
                config.setMaxSqlLength(Integer.parseInt(maxSqlLength));
            }

            String samplingRate = properties.getProperty("samplingRate");
            if (samplingRate != null) {
                config.setSamplingRate(Double.parseDouble(samplingRate));
            }

            String recordParams = properties.getProperty("recordParams");
            if (recordParams != null) {
                config.setRecordParams(Boolean.parseBoolean(recordParams));
            }

            String recordFullStack = properties.getProperty("recordFullStack");
            if (recordFullStack != null) {
                config.setRecordFullStack(Boolean.parseBoolean(recordFullStack));
            }

            String maxStackDepth = properties.getProperty("maxStackDepth");
            if (maxStackDepth != null) {
                config.setMaxStackDepth(Integer.parseInt(maxStackDepth));
            }
        }
    }

    /**
     * 关闭线程池
     */
    public void destroy() {
        LOGGING_EXECUTOR.shutdown();
    }

    /**
     * SQL 信息
     */
    private static class SqlInfo {
        String sql;
        Object parameter;
        List<ParameterMapping> parameterMappings;

        SqlInfo(String sql, Object parameter, List<ParameterMapping> parameterMappings) {
            this.sql = sql;
            this.parameter = parameter;
            this.parameterMappings = parameterMappings;
        }
    }

    /**
     * SQL 日志配置
     */
    public static class SqlLogConfig {
        /**
         * 是否启用日志
         */
        private boolean logEnabled = true;

        /**
         * 慢查询阈值（毫秒）
         */
        private long slowSqlThresholdMillis = 1000;

        /**
         * 最大 SQL 长度
         */
        private int maxSqlLength = 2000;

        /**
         * 采样率（默认 100%）
         */
        private double samplingRate = 1.0;

        /**
         * 是否记录参数
         */
        private boolean recordParams = true;

        /**
         * 是否记录完整堆栈
         */
        private boolean recordFullStack = false;

        /**
         * 堆栈最大深度
         */
        private int maxStackDepth = 50;

        public boolean isLogEnabled() {
            return logEnabled;
        }

        public void setLogEnabled(boolean logEnabled) {
            this.logEnabled = logEnabled;
        }

        public void setSlowQueryThreshold(long slowQueryThreshold) {
            this.slowSqlThresholdMillis = slowQueryThreshold;
        }

        public long getSlowQueryThreshold() {
            return slowSqlThresholdMillis;
        }

        public void setSlowQueryThresholdMillis(long slowQueryThresholdMillis) {
            this.slowSqlThresholdMillis = slowQueryThresholdMillis;
        }

        public long getSlowQueryThresholdMillis() {
            return slowSqlThresholdMillis;
        }

        public int getMaxSqlLength() {
            return maxSqlLength;
        }

        public void setMaxSqlLength(int maxSqlLength) {
            this.maxSqlLength = maxSqlLength;
        }

        public double getSamplingRate() {
            return samplingRate;
        }

        public void setSamplingRate(double samplingRate) {
            this.samplingRate = samplingRate;
        }

        public boolean isRecordParams() {
            return recordParams;
        }

        public void setRecordParams(boolean recordParams) {
            this.recordParams = recordParams;
        }

        public boolean isRecordFullStack() {
            return recordFullStack;
        }

        public void setRecordFullStack(boolean recordFullStack) {
            this.recordFullStack = recordFullStack;
        }

        public int getMaxStackDepth() {
            return maxStackDepth;
        }

        public void setMaxStackDepth(int maxStackDepth) {
            this.maxStackDepth = maxStackDepth;
        }

        public boolean isEnabled() {
            return logEnabled;
        }

        public void setEnabled(boolean enabled) {
            this.logEnabled = enabled;
        }
    }
}
