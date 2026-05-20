package com.eify.app.security;

import cn.hutool.jwt.JWTUtil;
import com.eify.common.context.CurrentContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("JwtAuthFilter")
class JwtAuthFilterTest {

    private static final String SECRET = "test-jwt-secret-for-unit-tests";

    JwtAuthFilter filter;
    HttpServletRequest request;
    HttpServletResponse response;
    FilterChain chain;
    StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new JwtAuthFilter();
        var field = JwtAuthFilter.class.getDeclaredField("jwtSecret");
        field.setAccessible(true);
        field.set(filter, SECRET);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        responseWriter = new StringWriter();
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    private String generateToken(Long userId, Long workspaceId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sub", userId);
        payload.put("wid", workspaceId);
        long now = System.currentTimeMillis() / 1000;
        payload.put("iat", now);
        payload.put("exp", now + 3600); // 1 小时后过期
        byte[] key = SECRET.getBytes(StandardCharsets.UTF_8);
        return JWTUtil.createToken(payload, key);
    }

    private void stubResponseWriter() throws Exception {
        PrintWriter writer = new PrintWriter(responseWriter);
        lenient().when(response.getWriter()).thenReturn(writer);
    }

    // ==================== public paths ====================

    @Nested
    @DisplayName("public paths bypass")
    class PublicPaths {

        @Test
        @DisplayName("放行 /api/v1/auth/login")
        void shouldBypassLoginPath() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assertThat(CurrentContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("放行 /api/v1/auth/register")
        void shouldBypassRegisterPath() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/v1/auth/register");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("放行 /api/v1/auth/refresh")
        void shouldBypassRefreshPath() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/v1/auth/refresh");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("放行 /doc.html")
        void shouldBypassDocHtml() throws Exception {
            when(request.getRequestURI()).thenReturn("/doc.html");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("放行 /v3/ 路径")
        void shouldBypassV3Paths() throws Exception {
            when(request.getRequestURI()).thenReturn("/v3/api-docs");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }

    // ==================== valid JWT ====================

    @Nested
    @DisplayName("valid JWT Bearer token")
    class ValidJwt {

        @Test
        @DisplayName("解析有效 JWT 设置 CurrentContext")
        void shouldSetContextFromValidJwt() throws Exception {
            stubResponseWriter(); // 防御性 stub：JWT 校验失败时会写响应体
            when(request.getRequestURI()).thenReturn("/api/v1/agents");
            String token = generateToken(100L, 20L);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

            AtomicLong capturedUserId = new AtomicLong();
            AtomicLong capturedWorkspaceId = new AtomicLong();
            doAnswer(inv -> {
                capturedUserId.set(CurrentContext.getUserId());
                capturedWorkspaceId.set(CurrentContext.getWorkspaceId());
                return null;
            }).when(chain).doFilter(request, response);

            filter.doFilter(request, response, chain);

            assertThat(capturedUserId.get()).isEqualTo(100L);
            assertThat(capturedWorkspaceId.get()).isEqualTo(20L);
            verify(chain).doFilter(request, response);
        }
    }

    // ==================== invalid JWT ====================

    @Nested
    @DisplayName("invalid JWT")
    class InvalidJwt {

        @Test
        @DisplayName("签名无效返回 401")
        void shouldReturn401ForInvalidSignature() throws Exception {
            stubResponseWriter();
            when(request.getRequestURI()).thenReturn("/api/v1/agents");
            when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token.here");

            filter.doFilter(request, response, chain);

            verify(response).setStatus(401);
            assertThat(responseWriter.toString()).contains("令牌无效");
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("token 过期返回 401")
        void shouldReturn401ForExpiredToken() throws Exception {
            stubResponseWriter();
            when(request.getRequestURI()).thenReturn("/api/v1/agents");
            // Generate token then tamper with it to cause validation failure
            when(request.getHeader("Authorization")).thenReturn("Bearer " +
                    "eyJhbGciOiJIUzI1NiIsInR5cGUiOiJKV1QifQ.eyJzdWIiOjEwMCwid2lkIjoyMH0.invalid");

            filter.doFilter(request, response, chain);

            verify(response).setStatus(401);
            verify(chain, never()).doFilter(any(), any());
        }
    }

    // ==================== missing auth header ====================

    @Nested
    @DisplayName("missing Authorization header")
    class MissingAuthHeader {

        @Test
        @DisplayName("无 Auth header 时返回 401")
        void shouldReturn401WithoutAuthHeader() throws Exception {
            stubResponseWriter();
            when(request.getRequestURI()).thenReturn("/api/v1/agents");
            when(request.getHeader("Authorization")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).setStatus(401);
            assertThat(responseWriter.toString()).contains("请先登录");
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("非 Bearer 格式的 Auth header 返回 401")
        void shouldReturn401ForNonBearerAuth() throws Exception {
            stubResponseWriter();
            when(request.getRequestURI()).thenReturn("/api/v1/agents");
            when(request.getHeader("Authorization")).thenReturn("Basic abc123");

            filter.doFilter(request, response, chain);

            verify(response).setStatus(401);
            assertThat(responseWriter.toString()).contains("请先登录");
            verify(chain, never()).doFilter(any(), any());
        }
    }

    // ==================== context cleanup ====================

    @Nested
    @DisplayName("context cleanup")
    class ContextCleanup {

        @Test
        @DisplayName("请求结束后清除 CurrentContext")
        void shouldClearContextAfterRequest() throws Exception {
            stubResponseWriter(); // 防御性 stub：JWT 校验失败时会写响应体
            when(request.getRequestURI()).thenReturn("/api/v1/agents");
            String token = generateToken(100L, 20L);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

            filter.doFilter(request, response, chain);

            assertThat(CurrentContext.getUserId()).isNull();
            assertThat(CurrentContext.getWorkspaceId()).isNull();
        }

        @Test
        @DisplayName("异常时也清除 CurrentContext")
        void shouldClearContextEvenOnException() throws Exception {
            stubResponseWriter(); // 防御性 stub：JWT 校验失败时会写响应体
            when(request.getRequestURI()).thenReturn("/api/v1/agents");
            String token = generateToken(100L, 20L);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            doThrow(new RuntimeException("chain error")).when(chain).doFilter(any(), any());

            try {
                filter.doFilter(request, response, chain);
            } catch (Exception ignored) {
            }

            assertThat(CurrentContext.getUserId()).isNull();
        }
    }
}
