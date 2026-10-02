package com.tang.sky.pojo;

import jakarta.validation.constraints.*;

/** 分开持久化模型、请求DTO与响应VO；Account含密码散列，绝不能直接作为接口响应。 */
public final class AuthModels {
    private AuthModels() {}
    public record Account(Long id, String username, String passwordHash, int status) {}
    public record Credentials(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,32}") String username,
            @NotBlank @Size(min = 8, max = 64) String password) {}
    public record UserView(Long id, String username, String role) {}
    public record LoginView(String token, UserView user) {}
}
