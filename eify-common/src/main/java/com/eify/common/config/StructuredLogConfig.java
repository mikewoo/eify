package com.eify.common.config;

import com.eify.common.log.AppVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 结构化日志配置
 *
 * <p>从配置文件读取应用版本并设置到统一的 AppVersion
 *
 * @author Claude
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "app")
public class StructuredLogConfig {

    /**
     * 应用版本
     */
    private String version = "1.0.0";

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        // 设置到统一的 AppVersion
        AppVersion.set(version);
        log.info("Structured log version set to: {}", version);
    }
}
