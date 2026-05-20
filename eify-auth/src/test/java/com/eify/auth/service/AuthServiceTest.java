package com.eify.auth.service;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthService 单元测试。
 * <p>
 * 使用 Mockito mock 所有依赖，只测业务逻辑。
 * 不启动 Spring 容器，单个测试 < 50ms。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    private static final String JWT_SECRET = "test-secret-key-for-testing";

    @Mock UserMapper userMapper;
    @Mock WorkspaceMapper workspaceMapper;
    @Mock WorkspaceMemberMapper memberMapper;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks
    AuthService authService;

    @BeforeEach
    void setUp() {
        CurrentContext.set(1L, 1L);
        ReflectionTestUtils.setField(authService, "jwtSecret", JWT_SECRET);
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    // ========== 辅助方法 ==========

    private User buildUser(Long id, String username, String email, int status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("$2a$10$encodedPassword");
        user.setDisplayName(username);
        user.setStatus(status);
        return user;
    }

    private Workspace buildWorkspace(Long id, String name) {
        Workspace ws = new Workspace();
        ws.setId(id);
        ws.setName(name);
        ws.setDescription("默认工作空间");
        return ws;
    }

    private WorkspaceMember buildMember(Long id, Long workspaceId, Long userId, String role) {
        WorkspaceMember member = new WorkspaceMember();
        member.setId(id);
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }

    private RegisterRequest buildRegisterRequest(String username, String email, String password, String displayName) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setEmail(email);
        req.setPassword(password);
        req.setDisplayName(displayName);
        return req;
    }

    private LoginRequest buildLoginRequest(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    // ========== register() ==========

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("P0 - 用户名已存在应抛 USERNAME_OR_EMAIL_EXISTS")
        void shouldThrowWhenUsernameExists() {
            // given
            RegisterRequest req = buildRegisterRequest("existing", "new@test.com", "test-password-not-for-production", null);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.register(req));
            assertEquals(ErrorCode.USERNAME_OR_EMAIL_EXISTS.getCode(), ex.getCode());
            assertEquals("用户名已存在", ex.getMessage());

            // 不应执行后续 insert
            verify(userMapper, never()).insert(any(User.class));
            verify(workspaceMapper, never()).insert(any(Workspace.class));
        }

        @Test
        @DisplayName("P0 - 邮箱已注册应抛 USERNAME_OR_EMAIL_EXISTS")
        void shouldThrowWhenEmailExists() {
            // given
            RegisterRequest req = buildRegisterRequest("newuser", "existing@test.com", "test-password-not-for-production", null);
            // 第一次 selectCount（用户名）返回 0，第二次（邮箱）返回 1
            when(userMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L)
                    .thenReturn(1L);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.register(req));
            assertEquals(ErrorCode.USERNAME_OR_EMAIL_EXISTS.getCode(), ex.getCode());
            assertEquals("邮箱已注册", ex.getMessage());

            verify(userMapper, never()).insert(any(User.class));
        }

        @Test
        @DisplayName("P1 - 注册成功应创建用户、工作空间和成员关系")
        void shouldCreateUserWorkspaceAndMember() {
            // given
            RegisterRequest req = buildRegisterRequest("newuser", "new@test.com", "test-password-not-for-production", "新用户");
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(passwordEncoder.encode("test-password-not-for-production")).thenReturn("$2a$10$encoded");
            when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(100L);
                return 1;
            });
            when(workspaceMapper.insert(any(Workspace.class))).thenAnswer(invocation -> {
                Workspace ws = invocation.getArgument(0);
                ws.setId(200L);
                return 1;
            });
            when(memberMapper.insert(any(WorkspaceMember.class))).thenReturn(1);

            // when
            AuthResponse response = authService.register(req);

            // then
            verify(userMapper).insert(any(User.class));
            verify(workspaceMapper).insert(any(Workspace.class));
            verify(memberMapper).insert(any(WorkspaceMember.class));

            // 验证返回结果
            assertNotNull(response.getAccessToken());
            assertNotNull(response.getRefreshToken());
            assertEquals(7200L, response.getExpiresIn());
            assertEquals(100L, response.getUser().getId());
            assertEquals("newuser", response.getUser().getUsername());
            assertEquals("new@test.com", response.getUser().getEmail());
            assertEquals("新用户", response.getUser().getDisplayName());
            assertEquals(200L, response.getWorkspace().getId());
            assertEquals("newuser 的工作空间", response.getWorkspace().getName());
            assertEquals("owner", response.getWorkspace().getRole());
        }

        @Test
        @DisplayName("P1 - displayName 为 null 时应默认使用用户名")
        void shouldDefaultDisplayNameToUsername() {
            // given
            RegisterRequest req = buildRegisterRequest("newuser", "new@test.com", "test-password-not-for-production", null);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(passwordEncoder.encode(any())).thenReturn("$2a$10$encoded");
            when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(100L);
                return 1;
            });
            when(workspaceMapper.insert(any(Workspace.class))).thenAnswer(invocation -> {
                Workspace ws = invocation.getArgument(0);
                ws.setId(200L);
                return 1;
            });
            when(memberMapper.insert(any(WorkspaceMember.class))).thenReturn(1);

            // when
            AuthResponse response = authService.register(req);

            // then
            assertEquals("newuser", response.getUser().getDisplayName());

            // 验证 User 实体的 displayName 设置正确
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).insert(userCaptor.capture());
            assertEquals("newuser", userCaptor.getValue().getDisplayName());
        }

        @Test
        @DisplayName("P1 - 注册时应设置用户状态为 1")
        void shouldSetUserStatusToActive() {
            // given
            RegisterRequest req = buildRegisterRequest("newuser", "new@test.com", "test-password-not-for-production", null);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(passwordEncoder.encode(any())).thenReturn("$2a$10$encoded");
            when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(100L);
                return 1;
            });
            when(workspaceMapper.insert(any(Workspace.class))).thenAnswer(invocation -> {
                Workspace ws = invocation.getArgument(0);
                ws.setId(200L);
                return 1;
            });
            when(memberMapper.insert(any(WorkspaceMember.class))).thenReturn(1);

            // when
            authService.register(req);

            // then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).insert(userCaptor.capture());
            assertEquals(1, userCaptor.getValue().getStatus());
        }

        @Test
        @DisplayName("P1 - 注册时成员角色应为 owner")
        void shouldSetMemberRoleToOwner() {
            // given
            RegisterRequest req = buildRegisterRequest("newuser", "new@test.com", "test-password-not-for-production", null);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(passwordEncoder.encode(any())).thenReturn("$2a$10$encoded");
            when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(100L);
                return 1;
            });
            when(workspaceMapper.insert(any(Workspace.class))).thenAnswer(invocation -> {
                Workspace ws = invocation.getArgument(0);
                ws.setId(200L);
                return 1;
            });
            when(memberMapper.insert(any(WorkspaceMember.class))).thenReturn(1);

            // when
            authService.register(req);

            // then
            ArgumentCaptor<WorkspaceMember> memberCaptor = ArgumentCaptor.forClass(WorkspaceMember.class);
            verify(memberMapper).insert(memberCaptor.capture());
            assertEquals("owner", memberCaptor.getValue().getRole());
            assertEquals(100L, memberCaptor.getValue().getUserId());
            assertEquals(200L, memberCaptor.getValue().getWorkspaceId());
        }
    }

    // ========== login() ==========

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("P0 - 用户不存在应抛 USER_NOT_FOUND")
        void shouldThrowWhenUserNotFound() {
            // given
            LoginRequest req = buildLoginRequest("nobody", "test-password-not-for-production");
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(req));
            assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 用户被禁用（status=0）应抛 USER_DISABLED")
        void shouldThrowWhenUserDisabled() {
            // given
            LoginRequest req = buildLoginRequest("testuser", "test-password-not-for-production");
            User user = buildUser(1L, "testuser", "test@test.com", 0);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(req));
            assertEquals(ErrorCode.USER_DISABLED.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 用户 status 为 null 应抛 USER_DISABLED")
        void shouldThrowWhenUserStatusNull() {
            // given
            LoginRequest req = buildLoginRequest("testuser", "test-password-not-for-production");
            User user = buildUser(1L, "testuser", "test@test.com", 1);
            user.setStatus(null);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(req));
            assertEquals(ErrorCode.USER_DISABLED.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 密码错误应抛 PASSWORD_INCORRECT")
        void shouldThrowWhenPasswordIncorrect() {
            // given
            LoginRequest req = buildLoginRequest("testuser", "wrong-password");
            User user = buildUser(1L, "testuser", "test@test.com", 1);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches("wrong-password", user.getPassword())).thenReturn(false);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(req));
            assertEquals(ErrorCode.PASSWORD_INCORRECT.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 用户无工作空间成员关系应抛 NOT_WORKSPACE_MEMBER")
        void shouldThrowWhenNoWorkspaceMember() {
            // given
            LoginRequest req = buildLoginRequest("testuser", "test-password-not-for-production");
            User user = buildUser(1L, "testuser", "test@test.com", 1);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches(any(), any())).thenReturn(true);
            when(memberMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(req));
            assertEquals(ErrorCode.NOT_WORKSPACE_MEMBER.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 工作空间不存在应抛 WORKSPACE_NOT_FOUND")
        void shouldThrowWhenWorkspaceNotFound() {
            // given
            LoginRequest req = buildLoginRequest("testuser", "test-password-not-for-production");
            User user = buildUser(1L, "testuser", "test@test.com", 1);
            WorkspaceMember member = buildMember(1L, 999L, 1L, "owner");
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches(any(), any())).thenReturn(true);
            when(memberMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(member));
            when(workspaceMapper.selectById(999L)).thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(req));
            assertEquals(ErrorCode.WORKSPACE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 登录成功应返回 token 和用户/工作空间信息")
        void shouldReturnAuthResponseOnSuccessfulLogin() {
            // given
            LoginRequest req = buildLoginRequest("testuser", "test-password-not-for-production");
            User user = buildUser(1L, "testuser", "test@test.com", 1);
            Workspace workspace = buildWorkspace(10L, "我的工作空间");
            WorkspaceMember member = buildMember(1L, 10L, 1L, "owner");
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches("test-password-not-for-production", user.getPassword())).thenReturn(true);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(memberMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(member));
            when(workspaceMapper.selectById(10L)).thenReturn(workspace);

            // when
            AuthResponse response = authService.login(req);

            // then
            assertNotNull(response.getAccessToken());
            assertNotNull(response.getRefreshToken());
            assertEquals(7200L, response.getExpiresIn());
            assertEquals(1L, response.getUser().getId());
            assertEquals("testuser", response.getUser().getUsername());
            assertEquals("test@test.com", response.getUser().getEmail());
            assertEquals(10L, response.getWorkspace().getId());
            assertEquals("我的工作空间", response.getWorkspace().getName());
            assertEquals("owner", response.getWorkspace().getRole());

            // 验证更新了最后登录时间
            verify(userMapper).updateById(any(User.class));
        }

        @Test
        @DisplayName("P1 - 登录成功应更新最后登录时间")
        void shouldUpdateLastLoginAt() {
            // given
            LoginRequest req = buildLoginRequest("testuser", "test-password-not-for-production");
            User user = buildUser(1L, "testuser", "test@test.com", 1);
            Workspace workspace = buildWorkspace(10L, "我的工作空间");
            WorkspaceMember member = buildMember(1L, 10L, 1L, "member");
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches(any(), any())).thenReturn(true);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(memberMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(member));
            when(workspaceMapper.selectById(10L)).thenReturn(workspace);

            // when
            authService.login(req);

            // then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(userCaptor.capture());
            assertNotNull(userCaptor.getValue().getLastLoginAt());
        }
    }

    // ========== refresh() ==========

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        @DisplayName("P0 - 无效 token 应抛 TOKEN_INVALID")
        void shouldThrowWhenTokenInvalid() {
            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.refresh("invalid-token"));
            assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 用户不存在应抛 USER_NOT_FOUND")
        void shouldThrowWhenUserNotFound() {
            // given - 生成有效 token 但用户不存在
            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", 999L);
            payload.put("wid", 1L);
            payload.put("role", "owner");
            payload.put("iat", System.currentTimeMillis() / 1000);
            payload.put("exp", System.currentTimeMillis() / 1000 + 3600);
            String token = JWTUtil.createToken(payload, JWT_SECRET.getBytes(StandardCharsets.UTF_8));

            when(userMapper.selectById(999L)).thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.refresh(token));
            assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 用户被禁用应抛 USER_NOT_FOUND")
        void shouldThrowWhenUserDisabled() {
            // given
            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", 1L);
            payload.put("wid", 1L);
            payload.put("role", "owner");
            payload.put("iat", System.currentTimeMillis() / 1000);
            payload.put("exp", System.currentTimeMillis() / 1000 + 3600);
            String token = JWTUtil.createToken(payload, JWT_SECRET.getBytes(StandardCharsets.UTF_8));

            User user = buildUser(1L, "testuser", "test@test.com", 0);
            when(userMapper.selectById(1L)).thenReturn(user);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.refresh(token));
            assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 工作空间不存在应抛 WORKSPACE_NOT_FOUND")
        void shouldThrowWhenWorkspaceNotFound() {
            // given
            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", 1L);
            payload.put("wid", 999L);
            payload.put("role", "owner");
            payload.put("iat", System.currentTimeMillis() / 1000);
            payload.put("exp", System.currentTimeMillis() / 1000 + 3600);
            String token = JWTUtil.createToken(payload, JWT_SECRET.getBytes(StandardCharsets.UTF_8));

            User user = buildUser(1L, "testuser", "test@test.com", 1);
            when(userMapper.selectById(1L)).thenReturn(user);
            when(workspaceMapper.selectById(999L)).thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.refresh(token));
            assertEquals(ErrorCode.WORKSPACE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 刷新成功应返回新 token")
        void shouldReturnNewTokensOnSuccessfulRefresh() {
            // given
            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", 1L);
            payload.put("wid", 10L);
            payload.put("role", "admin");
            payload.put("iat", System.currentTimeMillis() / 1000);
            payload.put("exp", System.currentTimeMillis() / 1000 + 3600);
            String token = JWTUtil.createToken(payload, JWT_SECRET.getBytes(StandardCharsets.UTF_8));

            User user = buildUser(1L, "testuser", "test@test.com", 1);
            Workspace workspace = buildWorkspace(10L, "我的工作空间");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(workspaceMapper.selectById(10L)).thenReturn(workspace);

            // when
            AuthResponse response = authService.refresh(token);

            // then
            assertNotNull(response.getAccessToken());
            assertNotNull(response.getRefreshToken());
            assertEquals(7200L, response.getExpiresIn());
            assertEquals(1L, response.getUser().getId());
            assertEquals("testuser", response.getUser().getUsername());
            assertEquals(10L, response.getWorkspace().getId());
            assertEquals("admin", response.getWorkspace().getRole());
        }
    }

    // ========== logout() ==========

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("P1 - 登出不应抛异常")
        void shouldNotThrowOnLogout() {
            assertDoesNotThrow(() -> authService.logout(1L));
        }
    }

    // ========== getCurrentUser() ==========

    @Nested
    @DisplayName("getCurrentUser()")
    class GetCurrentUserTests {

        @Test
        @DisplayName("P0 - 用户不存在应抛 USER_NOT_FOUND")
        void shouldThrowWhenUserNotFound() {
            // given
            when(userMapper.selectById(1L)).thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.getCurrentUser());
            assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 工作空间不存在应抛 WORKSPACE_NOT_FOUND")
        void shouldThrowWhenWorkspaceNotFound() {
            // given
            User user = buildUser(1L, "testuser", "test@test.com", 1);
            when(userMapper.selectById(1L)).thenReturn(user);
            when(workspaceMapper.selectById(1L)).thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.getCurrentUser());
            assertEquals(ErrorCode.WORKSPACE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 有成员关系时应返回成员角色")
        void shouldReturnMemberRole() {
            // given
            User user = buildUser(1L, "testuser", "test@test.com", 1);
            Workspace workspace = buildWorkspace(1L, "我的工作空间");
            WorkspaceMember member = buildMember(1L, 1L, 1L, "admin");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(workspaceMapper.selectById(1L)).thenReturn(workspace);
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(member);

            // when
            AuthResponse response = authService.getCurrentUser();

            // then
            assertEquals(1L, response.getUser().getId());
            assertEquals("testuser", response.getUser().getUsername());
            assertEquals(1L, response.getWorkspace().getId());
            assertEquals("admin", response.getWorkspace().getRole());
        }

        @Test
        @DisplayName("P1 - 无成员关系时应默认角色为 member")
        void shouldDefaultToMemberRoleWhenNoMembership() {
            // given
            User user = buildUser(1L, "testuser", "test@test.com", 1);
            Workspace workspace = buildWorkspace(1L, "我的工作空间");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(workspaceMapper.selectById(1L)).thenReturn(workspace);
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // when
            AuthResponse response = authService.getCurrentUser();

            // then
            assertEquals("member", response.getWorkspace().getRole());
        }
    }

    // ========== listUserWorkspaces() ==========

    @Nested
    @DisplayName("listUserWorkspaces()")
    class ListUserWorkspacesTests {

        @Test
        @DisplayName("P1 - 应返回用户所属的所有工作空间")
        void shouldReturnAllUserWorkspaces() {
            // given
            WorkspaceMember member1 = buildMember(1L, 10L, 1L, "owner");
            WorkspaceMember member2 = buildMember(2L, 20L, 1L, "member");
            when(memberMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(member1, member2));

            Workspace ws1 = buildWorkspace(10L, "工作空间A");
            Workspace ws2 = buildWorkspace(20L, "工作空间B");
            when(workspaceMapper.selectById(10L)).thenReturn(ws1);
            when(workspaceMapper.selectById(20L)).thenReturn(ws2);

            // when
            List<AuthResponse.WorkspaceInfo> result = authService.listUserWorkspaces();

            // then
            assertEquals(2, result.size());
            assertEquals(10L, result.get(0).getId());
            assertEquals("工作空间A", result.get(0).getName());
            assertEquals("owner", result.get(0).getRole());
            assertEquals(20L, result.get(1).getId());
            assertEquals("工作空间B", result.get(1).getName());
            assertEquals("member", result.get(1).getRole());
        }

        @Test
        @DisplayName("P1 - 用户无工作空间时应返回空列表")
        void shouldReturnEmptyListWhenNoWorkspaces() {
            // given
            when(memberMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            // when
            List<AuthResponse.WorkspaceInfo> result = authService.listUserWorkspaces();

            // then
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("P1 - 工作空间已被删除时应跳过")
        void shouldSkipDeletedWorkspaces() {
            // given
            WorkspaceMember member1 = buildMember(1L, 10L, 1L, "owner");
            WorkspaceMember member2 = buildMember(2L, 20L, 1L, "member");
            when(memberMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(member1, member2));

            when(workspaceMapper.selectById(10L)).thenReturn(buildWorkspace(10L, "工作空间A"));
            when(workspaceMapper.selectById(20L)).thenReturn(null); // 已删除

            // when
            List<AuthResponse.WorkspaceInfo> result = authService.listUserWorkspaces();

            // then
            assertEquals(1, result.size());
            assertEquals(10L, result.get(0).getId());
        }
    }

    // ========== switchWorkspace() ==========

    @Nested
    @DisplayName("switchWorkspace()")
    class SwitchWorkspaceTests {

        @Test
        @DisplayName("P0 - 不是工作空间成员应抛 NOT_WORKSPACE_MEMBER")
        void shouldThrowWhenNotWorkspaceMember() {
            // given
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.switchWorkspace(999L));
            assertEquals(ErrorCode.NOT_WORKSPACE_MEMBER.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 切换成功应返回新 token")
        void shouldReturnNewTokensOnSuccessfulSwitch() {
            // given
            WorkspaceMember member = buildMember(1L, 20L, 1L, "admin");
            User user = buildUser(1L, "testuser", "test@test.com", 1);
            Workspace workspace = buildWorkspace(20L, "目标工作空间");
            when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(member);
            when(userMapper.selectById(1L)).thenReturn(user);
            when(workspaceMapper.selectById(20L)).thenReturn(workspace);

            // when
            AuthResponse response = authService.switchWorkspace(20L);

            // then
            assertNotNull(response.getAccessToken());
            assertNotNull(response.getRefreshToken());
            assertEquals(1L, response.getUser().getId());
            assertEquals("testuser", response.getUser().getUsername());
            assertEquals(20L, response.getWorkspace().getId());
            assertEquals("目标工作空间", response.getWorkspace().getName());
            assertEquals("admin", response.getWorkspace().getRole());
        }
    }

    // ========== buildAuthResponse (JWT 验证) ==========

    @Nested
    @DisplayName("JWT Token 验证")
    class JwtTokenTests {

        @Test
        @DisplayName("P1 - 生成的 accessToken 应包含正确的 payload")
        void shouldGenerateAccessTokenWithCorrectPayload() {
            // given
            RegisterRequest req = buildRegisterRequest("testuser", "test@test.com", "test-password-not-for-production", null);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(passwordEncoder.encode(any())).thenReturn("$2a$10$encoded");
            when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(1L);
                return 1;
            });
            when(workspaceMapper.insert(any(Workspace.class))).thenAnswer(invocation -> {
                Workspace ws = invocation.getArgument(0);
                ws.setId(10L);
                return 1;
            });
            when(memberMapper.insert(any(WorkspaceMember.class))).thenReturn(1);

            // when
            AuthResponse response = authService.register(req);

            // then - 解析 accessToken 验证 payload
            var jwt = cn.hutool.jwt.JWTUtil.parseToken(response.getAccessToken());
            jwt.setKey(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
            assertTrue(jwt.validate(0));
            assertEquals(1L, Long.valueOf(jwt.getPayload("sub").toString()));
            assertEquals(10L, Long.valueOf(jwt.getPayload("wid").toString()));
            assertEquals("owner", jwt.getPayload("role").toString());
        }

        @Test
        @DisplayName("P1 - 生成的 refreshToken 应有更长的有效期")
        void shouldGenerateRefreshTokenWithLongerExpiry() {
            // given
            RegisterRequest req = buildRegisterRequest("testuser", "test@test.com", "test-password-not-for-production", null);
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(passwordEncoder.encode(any())).thenReturn("$2a$10$encoded");
            when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(1L);
                return 1;
            });
            when(workspaceMapper.insert(any(Workspace.class))).thenAnswer(invocation -> {
                Workspace ws = invocation.getArgument(0);
                ws.setId(10L);
                return 1;
            });
            when(memberMapper.insert(any(WorkspaceMember.class))).thenReturn(1);

            // when
            AuthResponse response = authService.register(req);

            // then - refreshToken 的 exp 应大于 accessToken 的 exp
            var accessJwt = cn.hutool.jwt.JWTUtil.parseToken(response.getAccessToken());
            var refreshJwt = cn.hutool.jwt.JWTUtil.parseToken(response.getRefreshToken());
            long accessExp = Long.valueOf(accessJwt.getPayload("exp").toString());
            long refreshExp = Long.valueOf(refreshJwt.getPayload("exp").toString());
            assertTrue(refreshExp > accessExp,
                    "refreshToken 有效期应大于 accessToken");
        }
    }
}
