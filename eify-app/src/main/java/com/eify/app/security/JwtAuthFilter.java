package com.eify.app.security;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.eify.common.context.CurrentContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * JWT 认证过滤器 — 解析 Authorization header，设置 CurrentContext。
 * <p>
 * 登录/注册接口无需认证，直接放行。
 */
@Slf4j
@Component
@Order(1)
public class JwtAuthFilter implements Filter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh"
    );

    @Value("${auth.jwt.secret}")
    private String jwtSecret;

    @Value("${auth.jwt.issuer:eify}")
    private String jwtIssuer;

    @Value("${auth.jwt.audience:eify-api}")
    private String jwtAudience;

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request,
                         jakarta.servlet.ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();

        // 公开接口直接放行
        if (PUBLIC_PATHS.contains(path) || path.startsWith("/doc.html") || path.startsWith("/v3/")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = req.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                JWT jwt = JWTUtil.parseToken(token);
                if (!jwt.setKey(jwtSecret.getBytes(StandardCharsets.UTF_8)).validate(0)) {
                    resp.setStatus(401);
                    resp.setContentType("application/json;charset=UTF-8");
                    resp.getWriter().write("{\"success\":false,\"error\":\"登录已过期，请重新登录\"}");
                    return;
                }

                // 显式检查 exp：避免 Hutool 版本差异导致过期 token 被放行
                Long exp = toLong(jwt.getPayload("exp"));
                long now = System.currentTimeMillis() / 1000;
                if (exp == null || exp <= now) {
                    resp.setStatus(401);
                    resp.setContentType("application/json;charset=UTF-8");
                    resp.getWriter().write("{\"success\":false,\"errorCode\":\"TOKEN_EXPIRED\",\"error\":\"登录已过期，请重新登录\"}");
                    return;
                }

                // 校验 iss / aud 防止跨环境 token 混用（如 dev token 误用于 prod）
                if (!jwtIssuer.equals(toString(jwt.getPayload("iss")))
                        || !jwtAudience.equals(toString(jwt.getPayload("aud")))) {
                    resp.setStatus(401);
                    resp.setContentType("application/json;charset=UTF-8");
                    resp.getWriter().write("{\"success\":false,\"error\":\"令牌无效\"}");
                    return;
                }

                Long userId = toLong(jwt.getPayload("sub"));
                Long workspaceId = toLong(jwt.getPayload("wid"));
                if (userId == null || workspaceId == null) {
                    resp.setStatus(401);
                    resp.setContentType("application/json;charset=UTF-8");
                    resp.getWriter().write("{\"success\":false,\"error\":\"令牌无效\"}");
                    return;
                }
                CurrentContext.set(userId, workspaceId);
                req.setAttribute("currentUserId", userId);
            } catch (Exception e) {
                log.warn("JWT 验证失败: {}", e.getMessage());
                resp.setStatus(401);
                resp.setContentType("application/json;charset=UTF-8");
                resp.getWriter().write("{\"success\":false,\"error\":\"令牌无效\"}");
                return;
            }
        } else {
            // 未提供有效 Authorization header，拒绝请求
            resp.setStatus(401);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"success\":false,\"error\":\"请先登录\"}");
            return;
        }

        try {
            chain.doFilter(request, response);
        } finally {
            CurrentContext.clear();
        }
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String toString(Object value) {
        return value != null ? value.toString() : null;
    }
}
