package com.tang.sky.controller.ai;

import com.tang.sky.ai.security.AiFailure;
import com.tang.sky.common.Result;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** AI专属安全错误字段；上游401不变成本地401，避免错误注销正常登录用户。 */
@Order(0)
@RestControllerAdvice(assignableTypes={AiAssistantController.class, AiStreamController.class,
    MealPlanController.class, RagController.class, AdminAiController.class})
public class AiFailureHandler {
    @ExceptionHandler(AiFailure.class)
    public ResponseEntity<Result<AiFailure.Details>> failure(AiFailure failure) {
        return ResponseEntity.status(503).body(new Result<>(503, failure.getMessage(), failure.details()));
    }
}
