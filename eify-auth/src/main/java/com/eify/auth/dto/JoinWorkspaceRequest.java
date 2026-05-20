package com.eify.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinWorkspaceRequest {

    @NotBlank(message = "邀请码不能为空")
    private String code;
}
