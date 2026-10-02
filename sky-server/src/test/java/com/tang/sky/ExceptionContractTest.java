package com.tang.sky;

import com.tang.sky.common.*;
import com.tang.sky.handler.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 测试专用Controller不进入生产扫描，验证异常转换和真实MVC参数校验。 */
class ExceptionContractTest {
    private MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void businessError() throws Exception {
        mvc.perform(get("/probe/business")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
    }
    @Test void invalidInput() throws Exception {
        mvc.perform(post("/probe").contentType("application/json").content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }
    @Test void malformedJson() throws Exception {
        mvc.perform(post("/probe").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
    }
    @Test void unknownErrorDoesNotLeak() throws Exception {
        mvc.perform(get("/probe/unknown")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("服务暂时不可用"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
    @RestController static class Probe {
        record Input(@NotBlank String name) {}
        @PostMapping("/probe") Result<String> input(@Valid @RequestBody Input input) { return Result.success(input.name()); }
        @GetMapping("/probe/business") void business() { throw new BusinessException(ErrorCode.CONFLICT); }
        @GetMapping("/probe/unknown") void unknown() { throw new IllegalStateException("internal private detail"); }
    }
}
