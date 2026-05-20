package com.eify.auth.service;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eify.common.context.CurrentContext;
import com.eify.auth.dto.AuthResponse;
import com.eify.auth.dto.LoginRequest;
import com.eify.auth.dto.RegisterRequest;
import com.eify.auth.entity.User;
import com.eify.auth.entity.Workspace;
import com.eify.auth.entity.WorkspaceMember;
import com.eify.auth.mapper.UserMapper;
import com.eify.auth.mapper.WorkspaceMapper;
import com.eify.auth.mapper.WorkspaceMemberMapper;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long ACCESS_EXPIRE_SECONDS = 2 * 60 * 60; // 2 小时
    private static final long REFRESH_EXPIRE_DAYS = 30;

    private final UserMapper userMapper;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${auth.jwt.secret}")
    private String jwtSecret;

    @Transactional(rollbackFor = Exception.class)
    public AuthResponse register(RegisterRequest req) {
        // 检查用户名和邮箱唯一性
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername())) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_OR_EMAIL_EXISTS, "用户名已存在");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, req.getEmail())) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_OR_EMAIL_EXISTS, "邮箱已注册");
        }

        // 创建用户
        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .displayName(req.getDisplayName() != null ? req.getDisplayName() : req.getUsername())
                .status(1)
                .build();
        userMapper.insert(user);

        // 创建默认工作空间
        Workspace workspace = Workspace.builder()
                .name(req.getUsername() + " 的工作空间")
                .description("默认工作空间")
                .build();
        workspaceMapper.insert(workspace);

        // 关联用户和工作空间（owner 角色）
        WorkspaceMember member = WorkspaceMember.builder()
                .workspaceId(workspace.getId())
                .userId(user.getId())
                .role("owner")
                .joinedAt(LocalDateTime.now())
                .build();
        memberMapper.insert(member);

        log.info("用户注册成功: userId={}, workspaceId={}", user.getId(), workspace.getId());

        // 签发 token
        return buildAuthResponse(user, workspace, "owner");
    }

    public AuthResponse login(LoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername()));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT);
        }

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 获取用户的工作空间（取第一个，优先取 role=owner 的）
        WorkspaceMember member = memberMapper.selectList(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getUserId, user.getId())
                        .orderByAsc(WorkspaceMember::getId)
                        .last("LIMIT 1")).stream().findFirst().orElse(null);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }
        Workspace workspace = workspaceMapper.selectById(member.getWorkspaceId());
        if (workspace == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        }

        log.info("用户登录成功: userId={}, workspaceId={}", user.getId(), workspace.getId());
        return buildAuthResponse(user, workspace, member.getRole());
    }

    public AuthResponse refresh(String refreshToken) {
        // 验证 refresh JWT
        JWT jwt;
        try {
            jwt = JWTUtil.parseToken(refreshToken);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        if (!jwt.setKey(jwtSecret.getBytes(StandardCharsets.UTF_8)).validate(0)) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        Long userId = Long.valueOf(jwt.getPayload("sub").toString());
        Long workspaceId = Long.valueOf(jwt.getPayload("wid").toString());
        String role = jwt.getPayload("role").toString();

        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        }

        return buildAuthResponse(user, workspace, role);
    }

    public void logout(Long userId) {
        // 无状态 JWT，客户端丢弃 token 即可
        log.info("用户登出: userId={}", userId);
    }

    /**
     * 获取当前用户信息（用于页面刷新后恢复用户信息）。
     */
    public AuthResponse getCurrentUser() {
        Long userId = CurrentContext.getUserId();
        Long workspaceId = CurrentContext.getWorkspaceId();

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        }
        WorkspaceMember member = memberMapper.selectOne(new LambdaQueryWrapper<WorkspaceMember>()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                .eq(WorkspaceMember::getUserId, userId));
        String role = member != null ? member.getRole() : "member";

        return buildAuthResponse(user, workspace, role);
    }

    /**
     * 获取当前用户的所有工作空间列表。
     */
    public List<AuthResponse.WorkspaceInfo> listUserWorkspaces() {
        Long userId = CurrentContext.getUserId();
        List<WorkspaceMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getUserId, userId));
        List<AuthResponse.WorkspaceInfo> list = new ArrayList<>();
        for (WorkspaceMember m : members) {
            Workspace ws = workspaceMapper.selectById(m.getWorkspaceId());
            if (ws != null) {
                list.add(AuthResponse.WorkspaceInfo.builder()
                        .id(ws.getId())
                        .name(ws.getName())
                        .role(m.getRole())
                        .build());
            }
        }
        return list;
    }

    /**
     * 切换工作空间 — 用当前用户和目标 workspace 签发新 token。
     */
    public AuthResponse switchWorkspace(Long targetWorkspaceId) {
        Long userId = CurrentContext.getUserId();
        WorkspaceMember member = memberMapper.selectOne(new LambdaQueryWrapper<WorkspaceMember>()
                .eq(WorkspaceMember::getWorkspaceId, targetWorkspaceId)
                .eq(WorkspaceMember::getUserId, userId));
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }
        User user = userMapper.selectById(userId);
        Workspace workspace = workspaceMapper.selectById(targetWorkspaceId);
        return buildAuthResponse(user, workspace, member.getRole());
    }

    private AuthResponse buildAuthResponse(User user, Workspace workspace, String role) {
        long now = System.currentTimeMillis() / 1000;
        long accessExp = now + ACCESS_EXPIRE_SECONDS;

        Map<String, Object> payload = new HashMap<>();
        payload.put("sub", user.getId());
        payload.put("wid", workspace.getId());
        payload.put("role", role);
        payload.put("iat", now);
        payload.put("exp", accessExp);

        String accessToken = JWTUtil.createToken(payload, jwtSecret.getBytes(StandardCharsets.UTF_8));

        // refresh token 有效期更长
        payload.put("exp", now + REFRESH_EXPIRE_DAYS * 86400);
        String refreshToken = JWTUtil.createToken(payload, jwtSecret.getBytes(StandardCharsets.UTF_8));

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(ACCESS_EXPIRE_SECONDS)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .displayName(user.getDisplayName())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .workspace(AuthResponse.WorkspaceInfo.builder()
                        .id(workspace.getId())
                        .name(workspace.getName())
                        .role(role)
                        .build())
                .build();
    }
}
