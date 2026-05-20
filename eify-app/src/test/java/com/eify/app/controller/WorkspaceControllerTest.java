package com.eify.app.controller;

import com.eify.auth.dto.AuthResponse;
import com.eify.auth.dto.CreateWorkspaceRequest;
import com.eify.auth.dto.JoinWorkspaceRequest;
import com.eify.auth.dto.MemberInfo;
import com.eify.auth.service.WorkspaceService;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkspaceController")
class WorkspaceControllerTest {

    @Mock WorkspaceService workspaceService;

    WorkspaceController controller;

    @BeforeEach
    void setUp() {
        controller = new WorkspaceController(workspaceService);
    }

    // ==================== create ====================

    @Nested
    @DisplayName("POST /")
    class Create {

        @Test
        @DisplayName("创建工作空间成功")
        void shouldCreateWorkspace() {
            CreateWorkspaceRequest req = new CreateWorkspaceRequest();
            req.setName("New WS");
            req.setDescription("desc");
            AuthResponse.WorkspaceInfo info = AuthResponse.WorkspaceInfo.builder()
                    .id(1L).name("New WS").role("owner").build();
            when(workspaceService.createWorkspace("New WS", "desc")).thenReturn(info);

            Result<AuthResponse.WorkspaceInfo> result = controller.create(req);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isSameAs(info);
        }
    }

    // ==================== generateInviteCode ====================

    @Nested
    @DisplayName("POST /{id}/invite-code")
    class GenerateInviteCode {

        @Test
        @DisplayName("生成邀请码成功")
        void shouldGenerateInviteCode() {
            when(workspaceService.generateInviteCode(5L)).thenReturn("ABC12345");

            Result<String> result = controller.generateInviteCode(5L);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isEqualTo("ABC12345");
        }
    }

    // ==================== join ====================

    @Nested
    @DisplayName("POST /join")
    class Join {

        @Test
        @DisplayName("凭邀请码加入成功")
        void shouldJoinByInviteCode() {
            JoinWorkspaceRequest req = new JoinWorkspaceRequest();
            req.setCode("INVITE01");
            AuthResponse.WorkspaceInfo info = AuthResponse.WorkspaceInfo.builder()
                    .id(3L).name("Joined WS").role("member").build();
            when(workspaceService.joinByInviteCode("INVITE01")).thenReturn(info);

            Result<AuthResponse.WorkspaceInfo> result = controller.join(req);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().getRole()).isEqualTo("member");
        }
    }

    // ==================== listMembers ====================

    @Nested
    @DisplayName("GET /{id}/members")
    class ListMembers {

        @Test
        @DisplayName("返回成员列表")
        void shouldListMembers() {
            List<MemberInfo> members = List.of(
                    MemberInfo.builder().userId(1L).username("u1").role("owner")
                            .joinedAt(LocalDateTime.now()).build());
            when(workspaceService.listMembers(5L)).thenReturn(members);

            Result<List<MemberInfo>> result = controller.listMembers(5L);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).hasSize(1);
        }
    }

    // ==================== removeMember ====================

    @Nested
    @DisplayName("DELETE /{id}/members/{userId}")
    class RemoveMember {

        @Test
        @DisplayName("移除成员成功")
        void shouldRemoveMember() {
            Result<Void> result = controller.removeMember(5L, 200L);

            assertThat(result.getCode()).isEqualTo(200);
            verify(workspaceService).removeMember(5L, 200L);
        }
    }

    // ==================== updateRole ====================

    @Nested
    @DisplayName("PUT /{id}/members/{userId}")
    class UpdateRole {

        @Test
        @DisplayName("更新角色成功")
        void shouldUpdateRole() {
            Result<Void> result = controller.updateRole(5L, 200L, Map.of("role", "admin"));

            assertThat(result.getCode()).isEqualTo(200);
            verify(workspaceService).updateRole(5L, 200L, "admin");
        }

        @Test
        @DisplayName("role 为 null 时抛异常")
        void shouldThrowWhenRoleNull() {
            assertThatThrownBy(() -> controller.updateRole(5L, 200L, Map.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("role 不能为空");
        }

        @Test
        @DisplayName("role 为 blank 时抛异常")
        void shouldThrowWhenRoleBlank() {
            assertThatThrownBy(() -> controller.updateRole(5L, 200L, Map.of("role", "  ")))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ==================== leave ====================

    @Nested
    @DisplayName("DELETE /{id}/leave")
    class Leave {

        @Test
        @DisplayName("退出工作空间成功")
        void shouldLeaveWorkspace() {
            Result<Void> result = controller.leave(5L);

            assertThat(result.getCode()).isEqualTo(200);
            verify(workspaceService).leaveWorkspace(5L);
        }
    }
}
