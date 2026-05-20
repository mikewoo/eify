package com.eify.workflow.engine.executor;

import tools.jackson.databind.ObjectMapper;
import com.eify.workflow.domain.config.ApiNodeConfig;
import com.eify.workflow.domain.config.NodeConfigParser;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.engine.ExecutionContext;
import com.eify.workflow.engine.NodeExecutor;
import com.eify.workflow.engine.NodeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class ApiNodeExecutor implements NodeExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_RESPONSE_BYTES = 512 * 1024; // 512KB
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newFixedThreadPool(4))
            .build();

    private static final Set<String> BLOCKED_SCHEMES = Set.of("file", "ftp", "jar", "gopher");
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "169.254.169.254", // AWS/cloud metadata
            "metadata.google.internal", // GCP metadata
            "100.100.100.200" // Alibaba Cloud metadata
    );

    @Override
    public String getType() {
        return "api_call";
    }

    @Override
    public NodeResult execute(WorkflowNode node, ExecutionContext ctx) {
        ApiNodeConfig config = (ApiNodeConfig) NodeConfigParser.parse("api_call", node.getConfig());
        if (config == null) {
            return NodeResult.fail("API 节点配置为空");
        }

        String url = ctx.resolveTemplate(config.url());
        int timeout = config.timeoutSeconds() != null ? config.timeoutSeconds() : 30;

        URI uri = validateUrl(url);
        if (uri == null) {
            return NodeResult.fail("URL 不安全或格式无效");
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(timeout));

            String method = config.method() != null ? config.method().toUpperCase() : "GET";
            if (config.headers() != null) {
                config.headers().forEach((k, v) -> builder.header(k, ctx.resolveTemplate(v)));
            }

            String body = null;
            if (config.body() != null) {
                body = ctx.resolveTemplate(MAPPER.writeValueAsString(config.body()));
                builder.method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpRequest request = builder.build();
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();
            if (responseBody != null && responseBody.length() > MAX_RESPONSE_BYTES) {
                responseBody = responseBody.substring(0, MAX_RESPONSE_BYTES);
                log.warn("API 响应已截断: nodeId={}, url={}, originalLength={}",
                        node.getId(), url, response.body().length());
            }

            log.info("API 调用完成: {} {} -> {}", method, url, response.statusCode());

            Map<String, Object> outputs = new HashMap<>();
            outputs.put(config.outputKey() != null ? config.outputKey() : "_apiResponse", responseBody);
            outputs.put("_apiStatus", response.statusCode());

            return NodeResult.ok(outputs);
        } catch (Exception e) {
            log.error("API 节点执行失败: nodeId={}, url={}", node.getId(), url, e);
            return NodeResult.fail("API 调用失败: " + e.getMessage());
        }
    }

    private URI validateUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            log.warn("API 节点 URL 格式无效: url={}", url, e.getMessage());
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null || BLOCKED_SCHEMES.contains(scheme.toLowerCase())) {
            log.warn("API 节点被阻止的协议: url={}, scheme={}", url, scheme);
            return null;
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            log.warn("API 节点不支持的协议: url={}, scheme={}", url, scheme);
            return null;
        }
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        if (BLOCKED_HOSTS.contains(host.toLowerCase())) {
            log.warn("API 节点被阻止的主机: url={}, host={}", url, host);
            return null;
        }
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (isBlockedAddress(addr)) {
                log.warn("API 节点被阻止的内网地址: url={}, addr={}", url, addr);
                return null;
            }
        } catch (UnknownHostException e) {
            // DNS 未解析不是安全风险，允许通过，由实际 HTTP 调用处理错误
        }
        return uri;
    }

    /** 检查地址是否为内网/回环/特殊地址，含 IPv6 支持 */
    private boolean isBlockedAddress(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()) {
            return true;
        }
        if (addr instanceof Inet4Address v4) {
            byte[] octets = v4.getAddress();
            // 169.254.x.x (link-local in IPv4, also covered by isLinkLocalAddress)
            // 0.0.0.0
            return octets[0] == 0 && octets[1] == 0 && octets[2] == 0 && octets[3] == 0;
        }
        if (addr instanceof Inet6Address v6) {
            // IPv6 loopback: ::1
            if (v6.isLoopbackAddress()) return true;
            // IPv6 link-local: fe80::
            if (v6.isLinkLocalAddress()) return true;
            // IPv6 site-local (deprecated but still used): fec0::
            if (v6.isSiteLocalAddress()) return true;
            // IPv4-mapped IPv6: ::ffff:x.x.x.x — resolve the embedded IPv4 and recheck
            byte[] raw = v6.getAddress();
            if (isIPv4MappedIPv6(raw)) {
                try {
                    byte[] v4Bytes = new byte[]{raw[12], raw[13], raw[14], raw[15]};
                    InetAddress v4Addr = Inet4Address.getByAddress(v4Bytes);
                    return isBlockedAddress(v4Addr);
                } catch (UnknownHostException ignored) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 检测 ::ffff:x.x.x.x 格式的 IPv4 映射 IPv6 地址 */
    private boolean isIPv4MappedIPv6(byte[] addr) {
        for (int i = 0; i < 10; i++) {
            if (addr[i] != 0) return false;
        }
        return addr[10] == (byte) 0xff && addr[11] == (byte) 0xff;
    }
}
