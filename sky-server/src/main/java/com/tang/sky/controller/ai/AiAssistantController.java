package com.tang.sky.controller.ai;
import com.tang.sky.ai.service.AiAssistantService;
import com.tang.sky.common.Result;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
/** 受用户鉴权保护的AI模块入口，状态明确标记mock/live，避免把演示误认为真实模型。 */
@RestController @RequestMapping("/user/ai")
public class AiAssistantController {
    private final AiAssistantService service;public AiAssistantController(AiAssistantService service){this.service=service;}
    @GetMapping("/status")public Result<Map<String,String>> status(){return Result.success(Map.of("mode",service.mode()));}
    @PostMapping("/chat")public Result<com.tang.sky.ai.model.ChatModels.Reply> chat(@jakarta.validation.Valid @RequestBody com.tang.sky.ai.model.ChatModels.Request input){return Result.success(service.chat(input));}
}
