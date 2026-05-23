package com.eify.mcp.service.impl;

import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.workspace.WorkspaceGuard;
import com.eify.mcp.domain.entity.McpServer;
import com.eify.mcp.mapper.McpServerMapper;
import com.eify.mcp.service.McpClientService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.annotation.Resource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientServiceImpl implements McpClientService {

    private static final int MAX_RETRIES = 2;
    /** 客户端连接池 TTL（5 分钟），超时后自动重建 */
    private static final long CLIENT_TTL_MS = 5 * 60 * 1000;

    private final McpServerMapper mcpServerMapper;

    @Resource(name = "mcpExecutor")
    private Executor mcpExecutor;

    /** 按 serverId 缓存 MCP 客户端（含创建时间戳），避免每次调用都重新握手 */
    private final ConcurrentHashMap<Long, ClientEntry> clientCache = new ConcurrentHashMap<>();

    /** 按 serverId 的锁对象，保证同一 server 的客户端创建和调用互斥 */
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    /** 按 serverId 缓存可用工具名集合，用于调用前验证工具是否存在 */
    private final ConcurrentHashMap<Long, Set<String>> toolCache = new ConcurrentHashMap<>();

    /** 客户端缓存条目 */
    private record ClientEntry(McpSyncClient client, long createdAt) {}

    // ---- Public API ----

    @Override
    public String callTool(Long serverId, String toolName, Map<String, Object> arguments) {
        try {
            return CompletableFuture.supplyAsync(
                    () -> callToolWithRetry(serverId, toolName, arguments), mcpExecutor).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.MCP_CALL_FAILED, "Interrupted");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ErrorCode.MCP_CALL_FAILED, extractMessage(e.getCause()));
        }
    }

    @Override
    public List<String> listTools(Long serverId) {
        try {
            return CompletableFuture.supplyAsync(() -> listToolsWithRetry(serverId), mcpExecutor).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.MCP_CALL_FAILED, "Interrupted");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ErrorCode.MCP_CALL_FAILED, extractMessage(e.getCause()));
        }
    }

    // ---- Retry wrappers ----

    private String callToolWithRetry(Long serverId, String toolName, Map<String, Object> arguments) {
        McpServer server = getServer(serverId);

        // 验证工具是否在服务器上存在
        Set<String> availableTools = getOrFetchTools(serverId, server.getEndpoint());
        if (!availableTools.isEmpty() && !availableTools.contains(toolName)) {
            String msg = String.format("工具 [%s] 在 MCP Server 上不存在 (%s)，可用工具: %s",
                    toolName, server.getEndpoint(), availableTools);
            log.error(msg);
            throw new BusinessException(ErrorCode.MCP_CALL_FAILED, msg);
        }

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return doCallTool(serverId, server.getEndpoint(), toolName, arguments);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES && isRetryable(e)) {
                    log.warn("MCP 调用失败，准备重试: serverId={}, toolName={}, endpoint={}, attempt={}/{}",
                            serverId, toolName, server.getEndpoint(), attempt + 1, MAX_RETRIES);
                    evictClient(serverId);
                } else {
                    log.error("MCP 工具调用最终失败: serverId={}, toolName={}, endpoint={}",
                            serverId, toolName, server.getEndpoint(), e);
                    throw new BusinessException(ErrorCode.MCP_CALL_FAILED,
                            String.format("工具 [%s] 调用失败 (%s): %s",
                                    toolName, server.getEndpoint(), extractMessage(e)));
                }
            }
        }
        throw new BusinessException(ErrorCode.MCP_CALL_FAILED,
                String.format("工具 [%s] 重试耗尽 (%s)", toolName, server.getEndpoint()));
    }

    private List<String> listToolsWithRetry(Long serverId) {
        McpServer server = getServer(serverId);
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return doListTools(serverId, server.getEndpoint());
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES && isRetryable(e)) {
                    log.warn("MCP listTools 失败，准备重试: serverId={}, endpoint={}, attempt={}/{}",
                            serverId, server.getEndpoint(), attempt + 1, MAX_RETRIES);
                    evictClient(serverId);
                } else {
                    log.error("MCP listTools 最终失败: serverId={}, endpoint={}",
                            serverId, server.getEndpoint(), e);
                    throw new BusinessException(ErrorCode.MCP_CALL_FAILED,
                            String.format("获取工具列表失败 (%s): %s",
                                    server.getEndpoint(), extractMessage(e)));
                }
            }
        }
        throw new BusinessException(ErrorCode.MCP_CALL_FAILED,
                String.format("获取工具列表重试耗尽 (%s)", server.getEndpoint()));
    }

    // ---- Core operations (use cached client) ----
    // McpSyncClient 底层使用 java.net.http.HttpClient，它是线程安全的，
    // 因此调用方法不需要额外同步；仅在客户端创建时保持互斥。

    private String doCallTool(Long serverId, String endpoint, String toolName, Map<String, Object> arguments) {
        McpSyncClient client = getOrCreateClient(serverId, endpoint);
        CallToolResult result = client.callTool(new CallToolRequest(toolName, arguments));
        List<Content> contents = result.content();
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        return contents.stream()
                .filter(c -> c instanceof TextContent)
                .map(c -> ((TextContent) c).text())
                .collect(Collectors.joining("\n"));
    }

    private List<String> doListTools(Long serverId, String endpoint) {
        McpSyncClient client = getOrCreateClient(serverId, endpoint);
        var result = client.listTools(null);
        var tools = result != null ? result.tools() : null;
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream()
                .map(t -> t.name())
                .collect(Collectors.toList());
    }

    // ---- Tool list cache ----

    private Set<String> getOrFetchTools(Long serverId, String endpoint) {
        Set<String> cached = toolCache.get(serverId);
        if (cached != null) {
            return cached;
        }

        Object lock = locks.computeIfAbsent(serverId, k -> new Object());
        synchronized (lock) {
            Set<String> doubleCheck = toolCache.get(serverId);
            if (doubleCheck != null) {
                return doubleCheck;
            }

            try {
                // 确保 client 已创建
                getOrCreateClient(serverId, endpoint);
                // 用缓存的 client 获取工具列表
                List<String> tools = doListTools(serverId, endpoint);
                Set<String> toolSet = Collections.unmodifiableSet(
                        tools.stream().collect(Collectors.toSet()));
                toolCache.put(serverId, toolSet);
                log.info("MCP 工具列表已缓存: serverId={}, count={}, tools={}",
                        serverId, toolSet.size(), toolSet);
                return toolSet;
            } catch (Exception e) {
                log.warn("获取 MCP 工具列表失败: serverId={}, endpoint={}, error={}",
                        serverId, endpoint, e.getMessage());
                return Collections.emptySet();
            }
        }
    }

    // ---- Client cache management ----

    private McpSyncClient getOrCreateClient(Long serverId, String endpoint) {
        ClientEntry existing = clientCache.get(serverId);
        if (existing != null) {
            // TTL 检查：过期自动驱逐并重建
            if (System.currentTimeMillis() - existing.createdAt() < CLIENT_TTL_MS) {
                return existing.client();
            }
            log.info("MCP 客户端 TTL 过期，重建: serverId={}", serverId);
            evictClient(serverId);
        }

        Object lock = locks.computeIfAbsent(serverId, k -> new Object());
        synchronized (lock) {
            ClientEntry doubleCheck = clientCache.get(serverId);
            if (doubleCheck != null) {
                return doubleCheck.client();
            }

            var transport = HttpClientStreamableHttpTransport.builder(endpoint)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            McpSyncClient client = McpClient.sync(transport)
                    .capabilities(ClientCapabilities.builder().build())
                    .build();

            client.initialize();
            log.info("MCP 客户端已创建并初始化: serverId={}, endpoint={}", serverId, endpoint);
            clientCache.put(serverId, new ClientEntry(client, System.currentTimeMillis()));
            return client;
        }
    }

    private void evictClient(Long serverId) {
        Object lock = locks.get(serverId);
        if (lock != null) {
            synchronized (lock) {
                doEvict(serverId);
            }
        } else {
            doEvict(serverId);
        }
    }

    private void doEvict(Long serverId) {
        ClientEntry entry = clientCache.remove(serverId);
        if (entry != null) {
            try {
                entry.client().closeGracefully();
                log.info("MCP 客户端已移除: serverId={}", serverId);
            } catch (Exception e) {
                log.debug("关闭 MCP 客户端时忽略异常: serverId={}", serverId, e);
            }
        }
        toolCache.remove(serverId);
    }

    /**
     * 主动清除工具缓存（MCP Server 更新/重连后调用）。
     */
    public void evictToolCache(Long serverId) {
        toolCache.remove(serverId);
        log.info("MCP 工具缓存已清除: serverId={}", serverId);
    }

    // ---- Helpers ----

    private McpServer getServer(Long serverId) {
        return WorkspaceGuard.requireInWorkspace(
                mcpServerMapper.selectById(serverId), ErrorCode.MCP_SERVER_NOT_FOUND);
    }

    /**
     * 判断异常是否可重试。
     * 仅连接/超时类 IO 错误可重试；业务错误、NPE、OOM 等不可恢复错误不重试。
     */
    private boolean isRetryable(Exception e) {
        if (e instanceof BusinessException) {
            return false;
        }
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof java.io.IOException
                    || cause instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static String extractMessage(Throwable e) {
        if (e instanceof McpError mcpErr) {
            var rpcErr = mcpErr.getJsonRpcError();
            return String.format("[%d] %s",
                    rpcErr.code(),
                    rpcErr.message() != null ? rpcErr.message() : "no message from server");
        }
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getSimpleName();
    }
}
