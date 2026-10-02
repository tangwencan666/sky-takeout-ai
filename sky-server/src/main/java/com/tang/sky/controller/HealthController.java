package com.tang.sky.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证 HTTP 请求能进入 Spring MVC 并返回 JSON，不访问业务数据。
 * 此接口只代表 Web 服务存活，不代表数据库可用；数据库由独立连接测试验证。
 * 统一 Result 返回类型将在阶段2引入，当前用一个固定响应保持骨架简洁。
 */
@RestController
public class HealthController {
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("code", 200, "message", "SkyTakeout AI is running");
    }
}
