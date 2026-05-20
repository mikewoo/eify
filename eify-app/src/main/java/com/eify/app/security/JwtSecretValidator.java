package com.eify.app.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 启动时校验 JWT 密钥安全性。
 * <p>
 * 在非 dev 环境下，若密钥为已知默认值或长度不足，拒绝启动。
 */
@Slf4j
@Component
public class JwtSecretValidator {

    private static final Set<String> KNOWN_DEFAULTS = Set.of(
            "eify-jwt-secret-key-2024-please-change-in-prod",
            "dev-eify-jwt-secret-not-for-production"
    );
    private static final int MIN_KEY_LENGTH = 16;

    @Value("${auth.jwt.secret}")
    private String jwtSecret;

    private final Environment env;

    public JwtSecretValidator(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        String[] profiles = env.getActiveProfiles();
        boolean isDev = false;
        for (String p : profiles) {
            if ("dev".equalsIgnoreCase(p)) {
                isDev = true;
                break;
            }
        }
        if (isDev) {
            log.info("开发环境，跳过 JWT 密钥安全检查");
            return;
        }

        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "生产环境必须配置 JWT 密钥！请设置环境变量 JWT_SECRET 或配置 auth.jwt.secret");
        }

        String trimmed = jwtSecret.trim();
        if (KNOWN_DEFAULTS.contains(trimmed)) {
            throw new IllegalStateException(
                    "JWT 密钥为已知默认值，存在安全风险！请使用生产环境安全的密钥（openssl rand -base64 32）");
        }

        if (trimmed.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "JWT 密钥太短（" + trimmed.length() + " 字符），最少需要 " + MIN_KEY_LENGTH + " 字符");
        }

        log.info("JWT 密钥安全检查通过 (长度: {})", trimmed.length());
    }
}
