package com.eify.common.log;

import java.net.InetAddress;

/**
 * 应用信息统一管理类
 *
 * <p>确保所有日志组件使用统一的应用信息
 *
 * @author Claude
 * @since 1.0.0
 */
public final class AppInfo {

    private AppInfo() {
        // 工具类，禁止实例化
    }

    /**
     * 应用名称
     */
    private static volatile String appName = "eify";

    /**
     * 服务器 IP
     */
    private static volatile String serverIp = "127.0.0.1";

    /**
     * 进程 ID
     */
    private static volatile String pid = String.valueOf(Thread.currentThread().getId());

    static {
        // 初始化应用名称
        appName = System.getProperty("spring.application.name", "eify");

        // 初始化服务器 IP
        try {
            serverIp = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            serverIp = "127.0.0.1";
        }

        // 初始化进程 ID
        String appPid = System.getProperty("app.pid");
        if (appPid != null) {
            pid = appPid;
        } else {
            String envPid = System.getenv().get("PID");
            if (envPid != null) {
                pid = envPid;
            }
        }
    }

    /**
     * 获取应用名称
     *
     * @return 应用名称
     */
    public static String getAppName() {
        return appName;
    }

    /**
     * 获取服务器 IP
     *
     * @return 服务器 IP
     */
    public static String getServerIp() {
        return serverIp;
    }

    /**
     * 获取进程 ID
     *
     * @return 进程 ID
     */
    public static String getPid() {
        return pid;
    }

    /**
     * 获取 Pod 名称（从环境变量读取）
     *
     * <p>支持 K8s 常用环境变量
     *
     * @return Pod 名称
     */
    public static String getPodName() {
        return System.getenv().getOrDefault("POD_NAME",
                System.getenv().getOrDefault("HOSTNAME", "local"));
    }
}
