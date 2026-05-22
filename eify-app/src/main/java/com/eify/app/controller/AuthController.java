package com.eify.app.controller;

import com.eify.common.context.CurrentContext;
import com.eify.auth.dto.AuthResponse;
import com.eify.auth.dto.LoginRequest;
import com.eify.auth.dto.RegisterRequest;
import com.eify.auth.service.AuthService;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@Tag(name = "认证管理", description = "用户注册、登录与工作空间切换")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;

    @Value("${auth.jwt.refresh-absolute-ttl-seconds:86400}")
    private long refreshAbsoluteTtlSeconds;

    @Value("${auth.jwt.cookie-secure:false}")
    private boolean cookieSecure;

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest req,
                                         HttpServletResponse response) {
        AuthResponse resp = authService.register(req);
        setRefreshCookie(response, resp.getRefreshToken());
        return Result.success(resp);
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest req,
                                      HttpServletResponse response) {
        AuthResponse resp = authService.login(req);
        setRefreshCookie(response, resp.getRefreshToken());
        return Result.success(resp);
    }

    @PostMapping("/refresh")
    public Result<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken,
            @RequestBody(required = false) java.util.Map<String, String> body,
            HttpServletResponse response) {
        String refreshToken = cookieToken;
        if (refreshToken == null && body != null) {
            refreshToken = body.get("refreshToken");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "refreshToken 不能为空");
        }
        AuthResponse resp = authService.refresh(refreshToken);
        setRefreshCookie(response, resp.getRefreshToken());
        return Result.success(resp);
    }

    @GetMapping("/me")
    public Result<AuthResponse> me() {
        AuthResponse resp = authService.getCurrentUser();
        return Result.success(resp);
    }

    @PostMapping("/logout")
    public Result<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        clearRefreshCookie(response);
        return Result.success();
    }

    @GetMapping("/workspaces")
    public Result<List<AuthResponse.WorkspaceInfo>> listWorkspaces() {
        List<AuthResponse.WorkspaceInfo> list = authService.listUserWorkspaces();
        return Result.success(list);
    }

    @PostMapping("/switch-workspace")
    public Result<AuthResponse> switchWorkspace(@RequestBody java.util.Map<String, Long> body,
                                                HttpServletResponse response) {
        Long workspaceId = body.get("workspaceId");
        if (workspaceId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "workspaceId 不能为空");
        }
        AuthResponse resp = authService.switchWorkspace(workspaceId);
        setRefreshCookie(response, resp.getRefreshToken());
        return Result.success(resp);
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofSeconds(refreshAbsoluteTtlSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
