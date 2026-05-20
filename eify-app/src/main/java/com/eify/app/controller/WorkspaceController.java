package com.eify.app.controller;

import com.eify.auth.dto.AuthResponse;
import com.eify.auth.dto.CreateWorkspaceRequest;
import com.eify.auth.dto.JoinWorkspaceRequest;
import com.eify.auth.dto.MemberInfo;
import com.eify.auth.service.WorkspaceService;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "工作空间管理", description = "工作空间 CRUD、成员管理与邀请")
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    public Result<AuthResponse.WorkspaceInfo> create(@Valid @RequestBody CreateWorkspaceRequest req) {
        AuthResponse.WorkspaceInfo info = workspaceService.createWorkspace(
                req.getName(), req.getDescription());
        return Result.success(info);
    }

    @PostMapping("/{id}/invite-code")
    public Result<String> generateInviteCode(@PathVariable Long id) {
        String code = workspaceService.generateInviteCode(id);
        return Result.success(code);
    }

    @PostMapping("/join")
    public Result<AuthResponse.WorkspaceInfo> join(@Valid @RequestBody JoinWorkspaceRequest req) {
        AuthResponse.WorkspaceInfo info = workspaceService.joinByInviteCode(req.getCode());
        return Result.success(info);
    }

    @GetMapping("/{id}/members")
    public Result<List<MemberInfo>> listMembers(@PathVariable Long id) {
        List<MemberInfo> members = workspaceService.listMembers(id);
        return Result.success(members);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        workspaceService.removeMember(id, userId);
        return Result.success();
    }

    @PutMapping("/{id}/members/{userId}")
    public Result<Void> updateRole(@PathVariable Long id, @PathVariable Long userId,
                                   @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null || role.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "role 不能为空");
        }
        workspaceService.updateRole(id, userId, role);
        return Result.success();
    }

    @DeleteMapping("/{id}/leave")
    public Result<Void> leave(@PathVariable Long id) {
        workspaceService.leaveWorkspace(id);
        return Result.success();
    }
}
