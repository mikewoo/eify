package com.eify.app.controller;

import com.eify.common.context.CurrentContext;
import com.eify.auth.dto.AuthResponse;
import com.eify.auth.dto.LoginRequest;
import com.eify.auth.dto.RegisterRequest;
import com.eify.auth.service.AuthService;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock AuthService authService;
    @Mock HttpServletResponse response;

    AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService);
        CurrentContext.set(100L, 10L);
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    // ==================== register ====================

    @Nested
    @DisplayName("POST /register")
    class Register {

        @Test
        @DisplayName("注册成功返回 AuthResponse")
        void shouldRegisterSuccessfully() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("testuser");
            req.setPassword("pass123");
            AuthResponse resp = AuthResponse.builder().accessToken("token").refreshToken("rt").build();
            when(authService.register(req)).thenReturn(resp);

            Result<AuthResponse> result = controller.register(req, response);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isSameAs(resp);
        }
    }

    // ==================== login ====================

    @Nested
    @DisplayName("POST /login")
    class Login {

        @Test
        @DisplayName("登录成功返回 AuthResponse")
        void shouldLoginSuccessfully() {
            LoginRequest req = new LoginRequest();
            req.setUsername("testuser");
            req.setPassword("pass123");
            AuthResponse resp = AuthResponse.builder().accessToken("token").refreshToken("rt").build();
            when(authService.login(req)).thenReturn(resp);

            Result<AuthResponse> result = controller.login(req, response);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isSameAs(resp);
        }
    }

    // ==================== refresh ====================

    @Nested
    @DisplayName("POST /refresh")
    class Refresh {

        @Test
        @DisplayName("cookie 和 body 都为空时抛异常")
        void shouldThrowWhenRefreshTokenNull() {
            assertThatThrownBy(() -> controller.refresh(null, Map.of(), response))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("refreshToken 不能为空");
        }

        @Test
        @DisplayName("cookie 为 null body 里是 blank 时抛异常")
        void shouldThrowWhenRefreshTokenBlank() {
            assertThatThrownBy(() -> controller.refresh(null, Map.of("refreshToken", "   "), response))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("通过 body 传入 refreshToken 刷新成功")
        void shouldRefreshSuccessfullyFromBody() {
            AuthResponse resp = AuthResponse.builder().accessToken("new-token").refreshToken("new-rt").build();
            when(authService.refresh("old-token")).thenReturn(resp);

            Result<AuthResponse> result = controller.refresh(null, Map.of("refreshToken", "old-token"), response);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isSameAs(resp);
        }

        @Test
        @DisplayName("通过 cookie 传入 refreshToken 刷新成功")
        void shouldRefreshSuccessfullyFromCookie() {
            AuthResponse resp = AuthResponse.builder().accessToken("new-token").refreshToken("new-rt").build();
            when(authService.refresh("cookie-token")).thenReturn(resp);

            Result<AuthResponse> result = controller.refresh("cookie-token", null, response);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isSameAs(resp);
        }
    }

    // ==================== me ====================

    @Nested
    @DisplayName("GET /me")
    class Me {

        @Test
        @DisplayName("返回当前用户信息")
        void shouldReturnCurrentUser() {
            AuthResponse resp = AuthResponse.builder().accessToken("t").build();
            when(authService.getCurrentUser()).thenReturn(resp);

            Result<AuthResponse> result = controller.me();

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isSameAs(resp);
        }
    }

    // ==================== logout ====================

    @Nested
    @DisplayName("POST /logout")
    class Logout {

        @Test
        @DisplayName("登出成功")
        void shouldLogoutSuccessfully() {
            Result<Void> result = controller.logout("some-refresh-token", response);

            assertThat(result.getCode()).isEqualTo(200);
            verify(authService).logout("some-refresh-token");
        }

        @Test
        @DisplayName("cookie 中无 token 时也能登出")
        void shouldLogoutWithoutToken() {
            Result<Void> result = controller.logout(null, response);

            assertThat(result.getCode()).isEqualTo(200);
            verify(authService).logout(null);
        }
    }

    // ==================== listWorkspaces ====================

    @Nested
    @DisplayName("GET /workspaces")
    class ListWorkspaces {

        @Test
        @DisplayName("返回工作空间列表")
        void shouldListWorkspaces() {
            List<AuthResponse.WorkspaceInfo> list = List.of(
                    AuthResponse.WorkspaceInfo.builder().id(1L).name("ws1").role("owner").build());
            when(authService.listUserWorkspaces()).thenReturn(list);

            Result<List<AuthResponse.WorkspaceInfo>> result = controller.listWorkspaces();

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).hasSize(1);
        }
    }

    // ==================== switchWorkspace ====================

    @Nested
    @DisplayName("POST /switch-workspace")
    class SwitchWorkspace {

        @Test
        @DisplayName("workspaceId 为空时抛异常")
        void shouldThrowWhenWorkspaceIdNull() {
            assertThatThrownBy(() -> controller.switchWorkspace(Map.of(), response))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("workspaceId 不能为空");
        }

        @Test
        @DisplayName("切换成功返回新 token")
        void shouldSwitchWorkspaceSuccessfully() {
            AuthResponse resp = AuthResponse.builder().accessToken("switched-token").refreshToken("new-rt").build();
            when(authService.switchWorkspace(20L)).thenReturn(resp);

            Result<AuthResponse> result = controller.switchWorkspace(Map.of("workspaceId", 20L), response);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isSameAs(resp);
        }
    }
}
