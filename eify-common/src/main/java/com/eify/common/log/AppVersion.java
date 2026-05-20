package com.eify.common.log;

/**
 * 应用版本统一管理类
 *
 * <p>确保所有日志组件使用统一的版本号
 *
 * <p>版本获取顺序（优先级从高到低）：
 * <ol>
 *   <li>系统属性 {@code app.version}</li>
 *   <li>环境变量 {@code APP_VERSION}</li>
 *   <li>默认值 {@code 1.0.0}</li>
 * </ol>
 *
 * <p>注意：Spring Boot 启动时通过 {@code StructuredLogConfig} 覆盖此值
 *
 * @author Claude
 * @since 1.0.0
 */
public final class AppVersion {

    private AppVersion() {
        // 工具类，禁止实例化
    }

    /**
     * 应用版本（静态初始化时尝试读取配置）
     *
     * <p>读取顺序：系统属性 → 环境变量 → 默认值
     */
    private static volatile String version = initializeVersion();

    /**
     * 静态初始化版本号
     *
     * <p>尝试从以下位置读取：
     * <ol>
     *   <li>系统属性 {@code app.version}</li>
     *   <li>环境变量 {@code APP_VERSION}</li>
     *   <li>默认值 {@code 1.0.0}</li>
     * </ol>
     *
     * @return 版本号
     */
    private static String initializeVersion() {
        // 优先从系统属性读取（支持 -Dapp.version=1.0.0-SNAPSHOT）
        String sysProp = System.getProperty("app.version");
        if (sysProp != null && !sysProp.isEmpty()) {
            return sysProp;
        }

        // 其次从环境变量读取
        String envVar = System.getenv("APP_VERSION");
        if (envVar != null && !envVar.isEmpty()) {
            return envVar;
        }

        // 默认值
        return "1.0.0";
    }

    /**
     * 获取应用版本
     *
     * @return 应用版本
     */
    public static String get() {
        return version;
    }

    /**
     * 设置应用版本（由 StructuredLogConfig 调用）
     *
     * @param version 应用版本
     */
    public static void set(String version) {
        if (version != null && !version.isEmpty()) {
            AppVersion.version = version;
        }
    }

    /**
     * 重置版本号（用于测试）
     */
    static void reset() {
        AppVersion.version = initializeVersion();
    }
}