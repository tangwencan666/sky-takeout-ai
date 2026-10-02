package com.tang.sky.controller;

import com.tang.sky.common.Result;
import com.tang.sky.pojo.AuthModels.*;
import com.tang.sky.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 普通注册始终创建USER，管理员仅允许独立登录入口，不提供匿名管理员注册。 */
@RestController
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service=service; }
    @PostMapping("/user/auth/register") public Result<UserView> register(@Valid @RequestBody Credentials input) { return Result.success(service.register(input)); }
    @PostMapping("/user/auth/login") public Result<LoginView> login(@Valid @RequestBody Credentials input) { return Result.success(service.login(input,false)); }
    @PostMapping("/admin/auth/login") public Result<LoginView> admin(@Valid @RequestBody Credentials input) { return Result.success(service.login(input,true)); }
    @GetMapping({"/user/me","/admin/me"}) public Result<UserView> me() { return Result.success(service.current()); }
}
