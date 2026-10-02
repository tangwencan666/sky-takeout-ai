package com.tang.sky.config;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** 仅显式离线初始化命令启用，无默认密码，不暴露管理员注册HTTP接口，不覆盖已有员工。 */
@Component
@ConditionalOnProperty(name = "sky.admin-init", havingValue = "true")
public class AdminInitializer implements CommandLineRunner {
    private final DataSource dataSource;
    private final ConfigurableApplicationContext context;
    public AdminInitializer(DataSource dataSource, ConfigurableApplicationContext context) {
        this.dataSource = dataSource; this.context = context;
    }
    public void run(String... args) {
        int code = 1;
        try {
            String username = System.getenv("ADMIN_INIT_USERNAME");
            String password = System.getenv("ADMIN_INIT_PASSWORD");
            if (username == null || !username.matches("[A-Za-z0-9_]{3,32}") || password == null
                || password.length() < 12 || password.length() > 64 || password.getBytes(StandardCharsets.UTF_8).length > 72)
                throw new IllegalArgumentException();
            try (var connection = dataSource.getConnection()) {
                if (!"sky_takeout_ai".equals(connection.getCatalog())) throw new IllegalStateException();
                // 唯一索引冲突直接失败，绝不重置/覆盖已有账号。
                try (var statement = connection.prepareStatement("INSERT INTO employee(username,password_hash) VALUES(?,?)")) {
                    statement.setString(1, username);
                    statement.setString(2, new BCryptPasswordEncoder(12).encode(password));
                    statement.executeUpdate();
                }
            }
            System.out.println("ADMIN_INIT=SUCCESS"); code = 0;
        } catch (Exception e) {
            System.err.println("ADMIN_INIT=FAILED；检查环境变量、数据库连接或用户名是否已经存在；未覆盖账号。");
        }
        final int exitCode = code;
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }
}
