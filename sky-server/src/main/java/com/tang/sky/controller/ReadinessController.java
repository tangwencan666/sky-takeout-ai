package com.tang.sky.controller;

import com.tang.sky.common.Result;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 存活接口不查数据库；此就绪接口必须真实建立连接并验证新项目库，避免密码错误时假就绪。 */
@RestController
public class ReadinessController {
    private static final Logger log=LoggerFactory.getLogger(ReadinessController.class);
    private static final String DATABASE="sky_takeout_ai";
    private final DataSource dataSource;

    public ReadinessController(DataSource dataSource) { this.dataSource=dataSource; }

    @GetMapping("/health/ready")
    public ResponseEntity<Result<?>> ready() {
        try(var connection=dataSource.getConnection()) {
            // 校验失败时不继续执行SQL，不能把其他数据库的可连接状态当成本项目就绪。
            if(!DATABASE.equals(connection.getCatalog())) {
                log.warn("Database readiness failed reason=catalog_mismatch");
                return unavailable();
            }
            try(var statement=connection.createStatement()) {
                statement.setQueryTimeout(3);
                try(var rows=statement.executeQuery("SELECT 1")) {
                    if(!rows.next() || rows.getInt(1)!=1) {
                        log.warn("Database readiness failed reason=unexpected_probe_result");
                        return unavailable();
                    }
                }
            }
            return ResponseEntity.ok(Result.success(Map.of("status","ready","database",DATABASE)));
        } catch(SQLException e) {
            // 驱动异常原文可能包含连接参数；只记录异常类型和规范化SQLState，不记录堆栈或凭据。
            String sqlState=e.getSQLState();
            if(sqlState==null || !sqlState.matches("[A-Z0-9]{5}")) sqlState="unknown";
            log.warn("Database readiness failed type={} sqlState={}",e.getClass().getSimpleName(),sqlState);
            return unavailable();
        } catch(RuntimeException e) {
            log.warn("Database readiness failed type={}",e.getClass().getSimpleName());
            return unavailable();
        }
    }

    private ResponseEntity<Result<?>> unavailable() {
        return ResponseEntity.status(503).body(Result.failure(503,"数据库暂不可用，请检查数据库服务与连接配置"));
    }
}
