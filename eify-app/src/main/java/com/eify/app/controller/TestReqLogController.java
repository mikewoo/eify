package com.eify.app.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 请求日志测试控制器
 * <p>
 * 用于测试 REQ 日志的响应记录功能
 *
 * @author Claude
 * @since 1.0.0
 */
@Profile("dev")
@RestController
@RequestMapping("/api/v1/test/req-log")
public class TestReqLogController {

    /**
     * 测试1：简单的成功响应（应记录响应摘要或根据采样率）
     */
    @GetMapping("/success")
    public Map<String, Object> successResponse() {
        return Map.of(
                "message", "Success response",
                "timestamp", LocalDateTime.now().toString(),
                "data", List.of("item1", "item2", "item3")
        );
    }

    /**
     * 测试2：错误响应（应完整记录错误响应）
     */
    @GetMapping("/error")
    public Map<String, Object> errorResponse() {
        return Map.of(
                "error", "Test error message",
                "code", "TEST_ERROR",
                "details", "This is a test error response for logging"
        );
    }

    /**
     * 测试3：大响应体（应被截断）
     */
    @GetMapping("/large")
    public Map<String, Object> largeResponse() {
        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeText.append("This is line ").append(i).append(" of the large response. ");
        }
        return Map.of(
                "message", "Large response",
                "data", largeText.toString()
        );
    }

    /**
     * 测试4：慢请求（应记录 WARN 日志）
     */
    @GetMapping("/slow")
    public Map<String, Object> slowRequest() throws InterruptedException {
        Thread.sleep(1500); // 1.5秒
        return Map.of(
                "message", "Slow response completed",
                "duration", "1500ms"
        );
    }

    /**
     * 测试5：同步 POST 请求
     */
    @PostMapping("/post")
    public Map<String, Object> postRequest(@RequestBody Map<String, Object> body) {
        return Map.of(
                "message", "POST request processed",
                "received", body,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    /**
     * 测试6：CompletableFuture 异步请求
     */
    @GetMapping("/future")
    public CompletableFuture<Map<String, Object>> futureResponse() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Map.of(
                    "message", "Future response completed",
                    "timestamp", LocalDateTime.now().toString(),
                    "thread", Thread.currentThread().getName()
            );
        });
    }

    /**
     * 测试7：多级嵌套响应
     */
    @GetMapping("/nested")
    public Map<String, Object> nestedResponse() {
        return Map.of(
                "level1", Map.of(
                        "level2", Map.of(
                                "level3", Map.of(
                                        "value", "deep nested value",
                                        "array", List.of("a", "b", "c")
                                )
                        )
                ),
                "timestamp", LocalDateTime.now().toString()
        );
    }

    /**
     * 测试8：空响应
     */
    @GetMapping("/empty")
    public void emptyResponse() {
        // 无响应体
    }

    /**
     * 测试9：包含特殊字符的响应
     */
    @GetMapping("/special")
    public Map<String, Object> specialCharsResponse() {
        return Map.of(
                "message", "Response with special chars",
                "special", "中文\n\t\r<script>alert('xss')</script>",
                "unicode", "中文测试",
                "emoji", "😀🎉🚀"
        );
    }

    /**
     * 测试10：模拟真实业务响应
     */
    @GetMapping("/business")
    public BusinessResponse businessResponse() {
        return new BusinessResponse(
                "Success",
                200,
                new User("user123", "test@example.com", "Test User"),
                List.of(
                        new Item("item1", "Product 1", 99.99),
                        new Item("item2", "Product 2", 149.99),
                        new Item("item3", "Product 3", 199.99)
                ),
                LocalDateTime.now().toString()
        );
    }

    /**
     * 测试11：随机延迟（测试慢请求告警）
     */
    @GetMapping("/random-delay")
    public Map<String, Object> randomDelay() throws InterruptedException {
        int delay = ThreadLocalRandom.current().nextInt(200, 2000);
        Thread.sleep(delay);
        return Map.of(
                "message", "Response with random delay",
                "delayMs", delay,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    /**
     * 测试12：404 Not Found
     */
    @GetMapping("/not-found")
    public Map<String, Object> notFound() {
        return Map.of(
                "error", "Resource not found",
                "code", 404
        );
    }

    // ==================== 内部类 ====================

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BusinessResponse {
        private String status;
        private Integer code;
        private User user;
        private List<Item> items;
        private String timestamp;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class User {
        private String id;
        private String email;
        private String name;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Item {
        private String id;
        private String name;
        private Double price;
    }
}
