package com.eify.provider.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import io.netty.resolver.DefaultAddressResolverGroup;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient 配置
 * <p>
 * 为 LLM API 调用配置超时时间和连接池
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        // 配置 HttpClient
        HttpClient httpClient = HttpClient.create()
                // 使用 JVM 内置 DNS 解析器（避免 Netty 异步解析器在 IPv6 链路本地 DNS 环境下失败）
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                // 连接超时：30 秒（增加以应对慢速网络）
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                // 响应超时：10 分钟（流式响应可能需要较长时间）
                .responseTimeout(Duration.ofMinutes(10))
                // 启用 Keep-Alive 以维持长连接
                .option(ChannelOption.SO_KEEPALIVE, true)
                // 读取超时：10 分钟
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(600, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(120, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
