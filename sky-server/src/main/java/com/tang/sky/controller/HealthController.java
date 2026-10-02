package com.tang.sky.controller;

import java.util.Map;
import com.tang.sky.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证 HTTP 请求能进入 Spring MVC 并返回 JSON，不访问业务数据。
 * 此接口只代表 Web 服务存活，不代表数据库可用；数据库由独立连接测试验证。
 * 阶段2统一为Result信封，具体健康信息放入data，便于前端统一处理。
 */
@RestController
public class HealthController {
    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        return Result.success(Map.of("status", "SkyTakeout AI is running"));
    }
}
