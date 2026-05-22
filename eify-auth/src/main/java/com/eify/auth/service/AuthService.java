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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务 — 遵循 OWASP/NIST 令牌安全建议：
 * <ul>
 *   <li>Access Token：30 分钟短有效期（OWASP 建议 15-30min）</li>
 *   <li>Refresh Token：24 小时绝对超时，family + count 滚动 + 重用检测</li>
 *   <li>Refresh Token 重用检测：同一 family 内每个 token 仅能用一次，重用即撤销整个 family</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String FAMILY_KEY_PREFIX = "refresh_family:";

    private final UserMapper userMapper;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${auth.jwt.secret}")
    private String jwtSecret;

    /** JWT Issuer，默认 eify，用于跨环境防混用 */
    @Value("${auth.jwt.issuer:eify}")
    private String jwtIssuer;

    /** JWT Audience，默认 eify-api */
    @Value("${auth.jwt.audience:eify-api}")
    private String jwtAudience;

    /** Access Token 有效期，默认 30 分钟（OWASP 建议 15-30min） */
    @Value("${auth.jwt.access-expire-seconds:1800}")
    private long accessExpireSeconds;

    /** Refresh Token 绝对超时，默认 24 小时（不滚动，从首次签发算起） */
    @Value("${auth.jwt.refresh-absolute-ttl-seconds:86400}")
    private long refreshAbsoluteTtlSeconds;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /** Redis 不可用时的内存降级存储 */
    private final ConcurrentHashMap<String, Long> familyStoreFallback = new ConcurrentHashMap<>();

    @Transactional(rollbackFor = Exception.class)
    public AuthResponse register(RegisterRequest req) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername())) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_OR_EMAIL_EXISTS, "用户名已存在");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, req.getEmail())) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_OR_EMAIL_EXISTS, "邮箱已注册");
        }

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .displayName(req.getDisplayName() != null ? req.getDisplayName() : req.getUsername())
                .status(1)
                .build();
        userMapper.insert(user);

        Workspace workspace = Workspace.builder()
                .name(req.getUsername() + " 的工作空间")
                .description("默认工作空间")
                .build();
        workspaceMapper.insert(workspace);

        WorkspaceMember member = WorkspaceMember.builder()
                .workspaceId(workspace.getId())
                .userId(user.getId())
                .role("owner")
                .joinedAt(LocalDateTime.now())
                .build();
        memberMapper.insert(member);

        log.info("用户注册成功: userId={}, workspaceId={}", user.getId(), workspace.getId());

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

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

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

    /**
     * 刷新 Access Token。
     * <p>
     * 每次使用 refresh token 时会滚动：签发新的 refresh token（family 不变，count+1），
     * 旧的 refresh token 立即失效。如果检测到已失效的 token 被重用，说明 token 可能被窃取，
     * 整个 family 会被撤销，所有设备上的会话均失效。
     */
    public AuthResponse refresh(String refreshToken) {
        JWT jwt;
        try {
            jwt = JWTUtil.parseToken(refreshToken);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        if (!jwt.setKey(jwtSecret.getBytes(StandardCharsets.UTF_8)).validate(0)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // 显式检查 exp
        Long exp = toLong(jwt.getPayload("exp"));
        long now = System.currentTimeMillis() / 1000;
        if (exp == null || exp <= now) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        // 校验 iss / aud 防止跨环境 token 混用
        String iss = toString(jwt.getPayload("iss"));
        String aud = toString(jwt.getPayload("aud"));
        if (!jwtIssuer.equals(iss) || !jwtAudience.equals(aud)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        Long userId = toLong(jwt.getPayload("sub"));
        Long workspaceId = toLong(jwt.getPayload("wid"));
        String role = jwt.getPayload("role").toString();
        String family = toString(jwt.getPayload("family"));
        Long count = toLong(jwt.getPayload("count"));

        if (userId == null || workspaceId == null || role == null || family == null || count == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // 原子化 family 验证 + 自增：防并发重放
        long newCount = incrementFamilyCount(family, count);
        if (newCount == -1) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }
        if (newCount == -2) {
            log.warn("[SECURITY] Refresh token 重用检测触发: family={}, userId={}, count={}",
                    family, userId, count);
            throw new BusinessException(ErrorCode.TOKEN_REUSE_DETECTED);
        }

        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        }

        // 签发新 token：access 30min，refresh 同 family 同绝对过期时间（iat 不变）
        Long jwtIat = toLong(jwt.getPayload("iat"));
        long originalIat = jwtIat != null ? jwtIat : now;
        long refreshExp = originalIat + refreshAbsoluteTtlSeconds;
        return buildTokens(user, workspace, role, family, (int) newCount, refreshExp, originalIat);
    }

    /**
     * 登出：撤销当前 refresh token 的 family，使所有设备上的会话失效。
     *
     * @param refreshToken 当前的 refresh token（从 HttpOnly cookie 获取）
     */
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            log.info("用户登出（无 refresh token）");
            return;
        }
        String family = extractFamily(refreshToken);
        if (family != null) {
            revokeFamily(family);
            log.info("用户登出并撤销 family: family={}", family);
        }
    }

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

    // ---- Token construction ----

    /** 新建 family，签发 access + refresh token */
    private AuthResponse buildAuthResponse(User user, Workspace workspace, String role) {
        long now = System.currentTimeMillis() / 1000;
        long refreshExp = now + refreshAbsoluteTtlSeconds;
        String family = UUID.randomUUID().toString();
        return buildTokens(user, workspace, role, family, 1, refreshExp);
    }

    /** 签发 token 对 */
    private AuthResponse buildTokens(User user, Workspace workspace, String role,
                                     String family, int count, long refreshExp) {
        return buildTokens(user, workspace, role, family, count, refreshExp,
                System.currentTimeMillis() / 1000);
    }

    /** 签发 token 对（指定 refresh token 的 iat，保持绝对过期时间不滚动） */
    private AuthResponse buildTokens(User user, Workspace workspace, String role,
                                     String family, int count, long refreshExp, long refreshIat) {
        long now = System.currentTimeMillis() / 1000;

        // Access Token: 30min, iss + aud 防跨环境混用
        Map<String, Object> accessPayload = new HashMap<>();
        accessPayload.put("sub", user.getId());
        accessPayload.put("wid", workspace.getId());
        accessPayload.put("role", role);
        accessPayload.put("iss", jwtIssuer);
        accessPayload.put("aud", jwtAudience);
        accessPayload.put("iat", now);
        accessPayload.put("exp", now + accessExpireSeconds);
        String accessToken = JWTUtil.createToken(accessPayload, jwtSecret.getBytes(StandardCharsets.UTF_8));

        // Refresh Token: family + count, 绝对 24h 不滚动, iss + aud
        Map<String, Object> refreshPayload = new HashMap<>();
        refreshPayload.put("sub", user.getId());
        refreshPayload.put("wid", workspace.getId());
        refreshPayload.put("role", role);
        refreshPayload.put("iss", jwtIssuer);
        refreshPayload.put("aud", jwtAudience);
        refreshPayload.put("iat", refreshIat);
        refreshPayload.put("exp", refreshExp);
        refreshPayload.put("family", family);
        refreshPayload.put("count", count);
        String refreshToken = JWTUtil.createToken(refreshPayload, jwtSecret.getBytes(StandardCharsets.UTF_8));

        // 持久化 family 状态
        storeFamilyCount(family, count, refreshExp);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessExpireSeconds)
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

    // ---- Family store (Redis primary, in-memory fallback) ----

    private void storeFamilyCount(String family, int count, long refreshExp) {
        String key = FAMILY_KEY_PREFIX + family;
        long now = System.currentTimeMillis() / 1000;
        long ttl = refreshExp - now;
        if (ttl <= 0) return;

        if (stringRedisTemplate != null) {
            stringRedisTemplate.opsForValue().set(key, String.valueOf(count), Duration.ofSeconds(ttl));
        } else {
            familyStoreFallback.put(key, (long) count);
        }
    }

    /**
     * 原子化验证并自增 family count。
     *
     * @return 新的 count 值；-1 表示 family 不存在/已过期；-2 表示重用检测触发
     */
    private long incrementFamilyCount(String family, long expectedCount) {
        String key = FAMILY_KEY_PREFIX + family;

        if (stringRedisTemplate != null) {
            Long newCount = stringRedisTemplate.opsForValue().increment(key);
            if (newCount == null) return -1;
            if (newCount != expectedCount + 1) {
                stringRedisTemplate.delete(key);
                return -2;
            }
            return newCount;
        }

        // 内存降级
        Long newCount = familyStoreFallback.compute(key, (k, v) -> {
            if (v == null) return null;
            if (v != expectedCount) return null;
            return v + 1;
        });
        if (newCount == null) {
            Long current = familyStoreFallback.get(key);
            familyStoreFallback.remove(key);
            return current == null ? -1 : -2;
        }
        return newCount;
    }

    // ---- Helpers ----

    /** 从 refresh token 中提取 family（解析失败返回 null，静默处理） */
    private String extractFamily(String refreshToken) {
        try {
            JWT jwt = JWTUtil.parseToken(refreshToken);
            if (!jwt.setKey(jwtSecret.getBytes(StandardCharsets.UTF_8)).validate(0)) return null;
            return toString(jwt.getPayload("family"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 撤销整个 token family */
    private void revokeFamily(String family) {
        String key = FAMILY_KEY_PREFIX + family;
        if (stringRedisTemplate != null) {
            stringRedisTemplate.delete(key);
        } else {
            familyStoreFallback.remove(key);
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
