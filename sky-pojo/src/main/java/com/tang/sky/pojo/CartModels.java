package com.tang.sky.pojo;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
/** 请求不包含userId或价格，金额从真实商品重算；口味作为备注且不能改变价格。 */
public final class CartModels {
    private CartModels() {}
    public record AddInput(@NotBlank @Pattern(regexp="DISH|SETMEAL") String itemType,@NotNull @Positive Long itemId,
        @NotNull @Size(max=200) String flavor,@Min(1) @Max(99) int quantity) {}
    public record Item(Long id,Long userId,String itemType,Long itemId,String flavor,int quantity) {}
    public record Product(String name,BigDecimal price) {}
    public record Line(Long id,String itemType,Long itemId,String flavor,int quantity,String name,BigDecimal unitPrice,BigDecimal subtotal,boolean available) {}
    public record View(List<Line> items,BigDecimal totalPrice,boolean checkoutAllowed) {}
}
