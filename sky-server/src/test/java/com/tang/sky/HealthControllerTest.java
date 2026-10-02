package com.tang.sky;

import com.tang.sky.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 只加载 MVC 切片，验证路由、状态码和 JSON 契约，不要求开发者提供数据库密码。 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void healthReturnsExpectedJson() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("SkyTakeout AI is running"));
    }
}
