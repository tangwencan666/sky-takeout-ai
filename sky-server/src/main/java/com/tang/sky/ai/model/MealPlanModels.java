package com.tang.sky.ai.model;
import com.tang.sky.pojo.CatalogModels.Dish;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
/** 模型只生成草案，最终菜品与金额由Java填充，不能直接把模型JSON当业务结果。 */
public final class MealPlanModels {
    private MealPlanModels(){}
    public record Request(@NotBlank @Size(max=2000)String message,@DecimalMin("0.01") @DecimalMax("10000")BigDecimal budget){}
    public record Draft(String reason,List<Long> dishIds){}
    public record AiMealPlan(String reason,List<Long> dishIds,List<Dish> dishes,BigDecimal totalPrice,BigDecimal budget,String mode){}
}
