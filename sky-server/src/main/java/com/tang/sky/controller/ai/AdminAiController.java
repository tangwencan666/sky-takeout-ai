package com.tang.sky.controller.ai;

import com.tang.sky.common.Result;
import com.tang.sky.ai.service.AdminAiService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/ai")
public class AdminAiController {
    public record Request(@NotBlank @Size(max = 2000) String message, @Min(1) @Max(90) int days) {}
    private final AdminAiService service;
    public AdminAiController(AdminAiService service) { this.service = service; }
    @PostMapping("/chat")
    public Result<AdminAiService.Analysis> chat(@Valid @RequestBody Request input) {
        return Result.success(service.analyze(input.message(), input.days()));
    }
}
