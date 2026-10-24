package com.workcollab.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;
import com.workcollab.entity.SystemRole;

@Data
@AllArgsConstructor
public class JwtAuthResponse {
    private String token;
    private String refreshToken;
    private UserDto user;

    @Data
    @AllArgsConstructor
    public static class UserDto {
        private UUID id;
        private String email;
        private String fullName;
        private SystemRole systemRole;
    }
}
