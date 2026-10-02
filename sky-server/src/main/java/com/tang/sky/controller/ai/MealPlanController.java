package com.tang.sky.controller.ai;
import com.tang.sky.ai.model.MealPlanModels.*;
import com.tang.sky.ai.service.MealPlanService;
import com.tang.sky.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
/** 推荐只返回计划，不创建购物车或订单；用户点击加购仍调用正常业务API。 */
@RestController
public class MealPlanController {
    private final MealPlanService service;public MealPlanController(MealPlanService service){this.service=service;}
    @PostMapping("/user/ai/meal-plan")public Result<AiMealPlan> plan(@Valid @RequestBody Request input){return Result.success(service.create(input));}
}
