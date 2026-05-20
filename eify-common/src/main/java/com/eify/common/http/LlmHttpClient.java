package com.eify.common.http;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * LLM HTTP 客户端
 * <p>
 * 封装了 RestTemplate 和 OkHttpClient，提供统一的 LLM API 调用接口
 * <ul>
 *   <li>RestTemplate：普通请求（连接超时 5s，读超时 60s）</li>
 *   <li>OkHttpClient：SSE 流式请求（连接超时 5s，读超时 120s）</li>
 *   <li>统一日志记录：URL、耗时、状态码</li>
 *   <li>统一异常处理：LlmApiException</li>
 * </ul>
 */
@Slf4j
@Component
public class LlmHttpClient {

    private final RestTemplate restTemplate;
    private final OkHttpClient okHttpClient;

    /**
     * 连接超时时间（秒）
     */
    private static final int CONNECT_TIMEOUT = 5;

    /**
     * RestTemplate 读超时时间（秒）
     */
    private static final int READ_TIMEOUT_REST = 60;

    /**
     * OkHttpClient 读超时时间（秒），覆盖慢模型推理场景
     */
    private static final int READ_TIMEOUT_OKHTTP = 300;

    public LlmHttpClient() {
        this.restTemplate = createRestTemplate();
        this.okHttpClient = createOkHttpClient();
    }

    /**
     * 创建 RestTemplate，使用 JDK HttpClient 实现连接池复用。
     * 显式指定 Executor 以配置连接池大小，避免默认的每个请求新建连接。
     */
    private RestTemplate createRestTemplate() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT))
                .executor(Executors.newFixedThreadPool(8))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_REST));

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setMessageConverters(Collections.singletonList(
                new org.springframework.http.converter.StringHttpMessageConverter()));

        return restTemplate;
    }

    /**
     * 创建 OkHttpClient，配置连接池以支持复用。
     * <p>
     * 连接池：最多 20 个空闲连接，保持 5 分钟，与线程池 8 线程 +
     * Dispatcher 默认并发匹配，避免高并发下频繁创建/销毁连接。
     */
    private OkHttpClient createOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_OKHTTP, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
                .build();
    }

    /**
     * 同步 POST 请求
     *
     * @param url     请求 URL
     * @param headers 请求头
     * @param body    请求体
     * @return 响应内容
     * @throws LlmApiException LLM API 异常
     */
    public String post(String url, Map<String, String> headers, String body) {
        long startTime = System.currentTimeMillis();
        String providerCode = extractProviderCode(url);

        try {
            log.info("[LLM] 请求开始 - URL: {}, Provider: {}", url, providerCode);

            // 创建请求实体
            HttpEntity<String> requestEntity = new HttpEntity<>(body, createHttpHeaders(headers));

            // 发送请求
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

            long costTime = System.currentTimeMillis() - startTime;
            int statusCode = response.getStatusCode().value();

            log.info("[LLM] 请求成功 - URL: {}, Provider: {}, 状态码: {}, 耗时: {}ms",
                    url, providerCode, statusCode, costTime);

            // 处理错误状态码
            if (statusCode >= 400) {
                throw handleErrorResponseAsException(url, providerCode, statusCode, response.getBody());
            }

            return response.getBody();

        } catch (org.springframework.web.client.ResourceAccessException e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("[LLM] 请求超时 - URL: {}, Provider: {}, 耗时: {}ms", url, providerCode, costTime);
            throw new LlmApiException(LlmApiException.ErrorType.TIMEOUT, providerCode, null, e.getMessage());

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            long costTime = System.currentTimeMillis() - startTime;
            int statusCode = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            log.error("[LLM] 客户端错误 - URL: {}, Provider: {}, 状态码: {}, 耗时: {}ms, 响应: {}",
                    url, providerCode, statusCode, costTime, responseBody);
            throw handleErrorResponseAsException(url, providerCode, statusCode, responseBody);

        } catch (org.springframework.web.client.HttpServerErrorException e) {
            long costTime = System.currentTimeMillis() - startTime;
            int statusCode = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            log.error("[LLM] 服务端错误 - URL: {}, Provider: {}, 状态码: {}, 耗时: {}ms, 响应: {}",
                    url, providerCode, statusCode, costTime, responseBody);
            throw handleErrorResponseAsException(url, providerCode, statusCode, responseBody);

        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("[LLM] 请求失败 - URL: {}, Provider: {}, 耗时: {}ms, 错误: {}",
                    url, providerCode, costTime, e.getMessage(), e);
            throw new LlmApiException(LlmApiException.ErrorType.NETWORK_ERROR, providerCode, null, e.getMessage());
        }
    }

    /**
     * 同步 GET 请求（用于连通性测试）
     *
     * @param url         请求 URL
     * @param headers     请求头
     * @param timeoutSec  超时时间（秒）
     * @return 响应内容
     * @throws LlmApiException LLM API 异常
     */
    public String get(String url, Map<String, String> headers, int timeoutSec) {
        long startTime = System.currentTimeMillis();
        String providerCode = extractProviderCode(url);

        try {
            log.info("[LLM] GET 请求开始 - URL: {}, Provider: {}, 超时: {}s", url, providerCode, timeoutSec);

            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofSeconds(timeoutSec));

            RestTemplate customTemplate = new RestTemplate(factory);
            customTemplate.setMessageConverters(Collections.singletonList(new org.springframework.http.converter.StringHttpMessageConverter()));

            // 创建请求实体
            HttpEntity<Void> requestEntity = new HttpEntity<>(createHttpHeaders(headers));

            // 发送请求
            ResponseEntity<String> response = customTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

            long costTime = System.currentTimeMillis() - startTime;
            int statusCode = response.getStatusCode().value();

            log.info("[LLM] GET 请求成功 - URL: {}, Provider: {}, 状态码: {}, 耗时: {}ms",
                    url, providerCode, statusCode, costTime);

            // 处理错误状态码
            if (statusCode >= 400) {
                throw handleErrorResponseAsException(url, providerCode, statusCode, response.getBody());
            }

            return response.getBody();

        } catch (org.springframework.web.client.ResourceAccessException e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("[LLM] GET 请求超时 - URL: {}, Provider: {}, 耗时: {}ms", url, providerCode, costTime);
            throw new LlmApiException(LlmApiException.ErrorType.TIMEOUT, providerCode, null, e.getMessage());

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            long costTime = System.currentTimeMillis() - startTime;
            int statusCode = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            log.error("[LLM] GET 客户端错误 - URL: {}, Provider: {}, 状态码: {}, 耗时: {}ms, 响应: {}",
                    url, providerCode, statusCode, costTime, responseBody);
            throw handleErrorResponseAsException(url, providerCode, statusCode, responseBody);

        } catch (org.springframework.web.client.HttpServerErrorException e) {
            long costTime = System.currentTimeMillis() - startTime;
            int statusCode = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            log.error("[LLM] GET 服务端错误 - URL: {}, Provider: {}, 状态码: {}, 耗时: {}ms, 响应: {}",
                    url, providerCode, statusCode, costTime, responseBody);
            throw handleErrorResponseAsException(url, providerCode, statusCode, responseBody);

        } catch (LlmApiException e) {
            throw e;
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("[LLM] GET 请求失败 - URL: {}, Provider: {}, 耗时: {}ms, 错误: {}",
                    url, providerCode, costTime, e.getMessage(), e);
            throw new LlmApiException(LlmApiException.ErrorType.NETWORK_ERROR, providerCode, null, e.getMessage());
        }
    }

    /**
     * SSE 流式 POST 请求
     *
     * @param url      请求 URL
     * @param headers  请求头
     * @param body     请求体
     * @param callback 回调函数，每行数据通过回调返回
     * @throws LlmApiException LLM API 异常
     */
    public void stream(String url, Map<String, String> headers, String body, LlmStreamCallback callback) {
        long startTime = System.currentTimeMillis();
        String providerCode = extractProviderCode(url);

        Request request = buildRequest(url, headers, body);
        Call call = okHttpClient.newCall(request);

        try {
            log.info("[LLM] SSE 请求开始 - URL: {}, Provider: {}", url, providerCode);

            Response response = call.execute();
            long costTime = System.currentTimeMillis() - startTime;
            int statusCode = response.code();

            log.info("[LLM] SSE 连接建立 - URL: {}, Provider: {}, 状态码: {}, 耗时: {}ms",
                    url, providerCode, statusCode, costTime);

            if (!response.isSuccessful()) {
                handleStreamError(url, providerCode, statusCode, response);
            }

            // 处理 SSE 流式响应
            try (ResponseBody responseBody = response.body();
                 BufferedReader reader = new BufferedReader(responseBody.charStream())) {
                if (responseBody == null) {
                    throw new LlmApiException(LlmApiException.ErrorType.NETWORK_ERROR, providerCode, statusCode, "响应体为空");
                }

                String line;
                int lineCount = 0;

                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if (!"[DONE]".equals(data)) {
                            callback.onData(data);
                        }
                    }
                }

                long totalCostTime = System.currentTimeMillis() - startTime;
                log.info("[LLM] SSE 请求完成 - URL: {}, Provider: {}, 行数: {}, 总耗时: {}ms",
                        url, providerCode, lineCount, totalCostTime);

            }

        } catch (SocketTimeoutException e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("[LLM] SSE 读超时 - URL: {}, Provider: {}, 耗时: {}ms", url, providerCode, costTime);
            throw new LlmApiException(LlmApiException.ErrorType.TIMEOUT, providerCode, null, e.getMessage());

        } catch (IOException e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("[LLM] SSE 请求失败 - URL: {}, Provider: {}, 耗时: {}ms, 错误: {}",
                    url, providerCode, costTime, e.getMessage(), e);
            throw new LlmApiException(LlmApiException.ErrorType.NETWORK_ERROR, providerCode, null, e.getMessage());

        } finally {
            call.cancel();
        }
    }

    /**
     * 构建 OkHttp 请求
     */
    private Request buildRequest(String url, Map<String, String> headers, String body) {
        RequestBody requestBody = RequestBody.create(
                okhttp3.MediaType.parse("application/json; charset=utf-8"),
                body
        );

        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(requestBody);

        // 添加请求头
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        return builder.build();
    }

    /**
     * 创建 HttpHeaders
     */
    private HttpHeaders createHttpHeaders(Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (headers != null) {
            headers.forEach(httpHeaders::add);
        }
        httpHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return httpHeaders;
    }

    /**
     * 从 URL 提取提供商标识，取 host 的第二级域名作为代码。
     * 例如 api.openai.com → openai，localhost:11434 → localhost。
     */
    private String extractProviderCode(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return "unknown";
            String[] parts = host.split("\\.");
            if (parts.length >= 2) return parts[parts.length - 2];
            return parts[0];
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 处理错误响应（普通请求）- 返回异常
     */
    private LlmApiException handleErrorResponseAsException(String url, String providerCode, int statusCode, String responseBody) {
        LlmApiException.ErrorType errorType;

        if (statusCode == 401) {
            errorType = LlmApiException.ErrorType.AUTH_FAILED;
        } else if (statusCode == 429) {
            errorType = LlmApiException.ErrorType.RATE_LIMITED;
        } else if (statusCode == 408 || statusCode == 504) {
            errorType = LlmApiException.ErrorType.TIMEOUT;
        } else {
            errorType = LlmApiException.ErrorType.UNKNOWN;
        }

        String errorMessage = extractErrorMessage(responseBody);
        log.error("[LLM] 请求失败 - URL: {}, Provider: {}, 状态码: {}, 类型: {}, 消息: {}",
                url, providerCode, statusCode, errorType, errorMessage);

        return new LlmApiException(errorType, providerCode, statusCode, errorMessage);
    }

    /**
     * 处理流式请求错误
     */
    private void handleStreamError(String url, String providerCode, int statusCode, Response response) {
        LlmApiException.ErrorType errorType;

        if (statusCode == 401) {
            errorType = LlmApiException.ErrorType.AUTH_FAILED;
        } else if (statusCode == 429) {
            errorType = LlmApiException.ErrorType.RATE_LIMITED;
        } else {
            errorType = LlmApiException.ErrorType.UNKNOWN;
        }

        String errorMessage = "SSE 请求失败，状态码: " + statusCode;
        log.error("[LLM] SSE 请求失败 - URL: {}, Provider: {}, 类型: {}, 消息: {}",
                url, providerCode, errorType, errorMessage);

        throw new LlmApiException(errorType, providerCode, statusCode, errorMessage);
    }

    /**
     * 从响应体中提取错误信息
     */
    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "无错误信息";
        }

        try {
            // 尝试解析 JSON 格式错误响应
            Map<String, Object> json = JSON.parseObject(responseBody, new com.alibaba.fastjson2.TypeReference<Map<String, Object>>() {});
            Object error = json.get("error");
            Object message = json.get("message");

            if (error != null) {
                return error.toString();
            }
            if (message != null) {
                return message.toString();
            }

            return responseBody;
        } catch (Exception e) {
            return responseBody;
        }
    }

    /**
     * SSE 流式回调接口
     */
    public interface LlmStreamCallback {
        /**
         * 接收 SSE 数据
         *
         * @param data 数据内容
         */
        void onData(String data);
    }
}
