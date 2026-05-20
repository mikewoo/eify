package com.eify.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private UserInfo user;
    private WorkspaceInfo workspace;

    @Data
    @Builder
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String displayName;
        private String avatarUrl;
    }

    @Data
    @Builder
    public static class WorkspaceInfo {
        private Long id;
        private String name;
        private String role;
    }
}
