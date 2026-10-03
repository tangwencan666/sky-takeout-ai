package com.tang.sky.controller.ai;
import com.tang.sky.ai.service.AiAssistantService;
import com.tang.sky.ai.service.AiProviderService;
import com.tang.sky.common.Result;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
/** 受用户鉴权保护的AI模块入口，状态明确标记mock/live，避免把演示误认为真实模型。 */
@RestController @RequestMapping("/user/ai")
public class AiAssistantController {
    private final AiAssistantService service;private final AiProviderService provider;
    public AiAssistantController(AiAssistantService service,AiProviderService provider){this.service=service;this.provider=provider;}
    @GetMapping("/status")public Result<AiProviderService.Status> status(){return Result.success(provider.status());}
    @PostMapping("/probe")public Result<AiProviderService.Probe> probe(){return Result.success(provider.probe());}
    @PostMapping("/chat")public Result<com.tang.sky.ai.model.ChatModels.Reply> chat(@jakarta.validation.Valid @RequestBody com.tang.sky.ai.model.ChatModels.Request input){return Result.success(service.chat(input));}
}
