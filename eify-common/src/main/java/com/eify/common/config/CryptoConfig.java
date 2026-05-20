package com.eify.common.config;

import com.eify.common.crypto.CryptoUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 加密配置 — 从环境变量加载 KEK 并暴露为 Spring Bean。
 * 生产环境必须通过 CRYPTO_KEK 环境变量注入，开发环境使用默认值。
 */
@Configuration
public class CryptoConfig {

    @Value("${crypto.kek:}")
    private String kekBase64;

    private volatile byte[] cachedKek;

    @Bean
    public byte[] cryptoKek() {
        if (kekBase64 == null || kekBase64.isBlank()) {
            throw new IllegalStateException(
                    "CRYPTO_KEK 未配置。开发环境可在 application-dev.yml 中设置默认值，" +
                    "生产环境必须通过环境变量 CRYPTO_KEK 注入。生成密钥: openssl rand -base64 32");
        }
        return CryptoUtil.decodeKek(kekBase64);
    }
}
