package com.eify.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MemberInfo {
    private Long userId;
    private String username;
    private String displayName;
    private String email;
    private String role;
    private LocalDateTime joinedAt;
}
