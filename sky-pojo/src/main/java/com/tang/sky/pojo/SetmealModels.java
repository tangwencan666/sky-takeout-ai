package com.tang.sky.pojo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
/** 套餐组成是独立关联模型，数量属于关联本身，不能塞进菜品实体。 */
public final class SetmealModels {
    private SetmealModels() {}
    public record Setmeal(Long id,Long categoryId,String name,BigDecimal price,String description,int status) {}
    public record Line(Long dishId,String name,int quantity) {}
    public record LineInput(@NotNull @Positive Long dishId,@Min(1) @Max(99) int quantity) {}
    public record Input(@NotNull @Positive Long categoryId,@NotBlank @Size(max=100) String name,
        @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal price,
        @NotNull @Size(max=500) String description,@NotEmpty @Size(max=30) List<@Valid LineInput> dishes) {}
    public record View(Setmeal setmeal,List<Line> dishes) {}
}
