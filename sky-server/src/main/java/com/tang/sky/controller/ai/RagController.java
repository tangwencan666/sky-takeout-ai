package com.tang.sky.controller.ai;
import com.tang.sky.ai.model.ChatModels.Request;
import com.tang.sky.ai.rag.RagService;
import com.tang.sky.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
/** 规则问答独立于订单查询：RAG用于规则证据，实时订单仍由受控Tool读取。 */
@RestController
public class RagController {
    private final RagService service;public RagController(RagService service){this.service=service;}
    @PostMapping("/user/ai/rules")public Result<RagService.Answer> answer(@Valid @RequestBody Request input){return Result.success(service.answer(input.message(),input.conversationId()));}
}
