package com.tang.sky.pojo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/** Entity表达数据库记录，Input表达允许修改的字段，View组合菜品和口味而不让Controller拼装SQL。 */
public final class CatalogModels {
    private CatalogModels() {}
    public record Category(Long id, String name, int type, int sort, int status) {}
    public record CategoryInput(@NotBlank @Size(max=50) String name, @Min(1) @Max(2) int type, @Min(0) int sort) {}
    public record Dish(Long id, Long categoryId, String name, BigDecimal price, String description, String image, int status) {}
    public record Flavor(Long id, Long dishId, String name, String options) {}
    public record FlavorInput(@NotBlank @Size(max=50) String name, @NotBlank @Size(max=500) String options) {}
    public record DishInput(@NotNull @Positive Long categoryId, @NotBlank @Size(max=100) String name,
            @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal price,
            @NotNull @Size(max=500) String description,
            @NotNull @Size(max=500) @Pattern(regexp="^$|^https?://[^\\s]+$|^/(?!/)[^\\s]*$",message="图片必须为HTTP(S)地址或站内路径") String image,
            @NotNull @Size(max=20) List<@Valid FlavorInput> flavors) {}
    public record DishView(Dish dish, List<Flavor> flavors) {}
    public record PageView<T>(long total, int page, int size, List<T> records) {}
}
