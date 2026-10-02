package com.tang.sky;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL 集成测试：需显式设置 RUN_DB_TESTS=true 并在本机提供 DB_PASSWORD。
 * 默认跳过必须如实报告，不能用 MVC 测试代替数据库验收；启用后连接失败会使构建失败。
 * 只验证连接与当前数据库，不创建表，也不读写旧项目业务数据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class DatabaseConnectionTest {
    @Autowired
    private DataSource dataSource;

    @Test
    void connectsToDedicatedProjectDatabase() throws Exception {
        // try-with-resources 在断言失败时也归还连接，避免耗尽连接池。
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(5), "MySQL连接必须有效");
            assertEquals("sky_takeout_ai", connection.getCatalog(), "必须连接新项目独立数据库");
        }
    }
}
