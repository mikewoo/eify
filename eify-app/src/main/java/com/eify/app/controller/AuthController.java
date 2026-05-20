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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "认证管理", description = "用户注册、登录与工作空间切换")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        AuthResponse resp = authService.register(req);
        return Result.success(resp);
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        AuthResponse resp = authService.login(req);
        return Result.success(resp);
    }

    @PostMapping("/refresh")
    public Result<AuthResponse> refresh(@RequestBody java.util.Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "refreshToken 不能为空");
        }
        AuthResponse resp = authService.refresh(refreshToken);
        return Result.success(resp);
    }

    @GetMapping("/me")
    public Result<AuthResponse> me() {
        AuthResponse resp = authService.getCurrentUser();
        return Result.success(resp);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(CurrentContext.getUserId());
        return Result.success();
    }

    @GetMapping("/workspaces")
    public Result<List<AuthResponse.WorkspaceInfo>> listWorkspaces() {
        List<AuthResponse.WorkspaceInfo> list = authService.listUserWorkspaces();
        return Result.success(list);
    }

    @PostMapping("/switch-workspace")
    public Result<AuthResponse> switchWorkspace(@RequestBody java.util.Map<String, Long> body) {
        Long workspaceId = body.get("workspaceId");
        if (workspaceId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "workspaceId 不能为空");
        }
        AuthResponse resp = authService.switchWorkspace(workspaceId);
        return Result.success(resp);
    }
}
