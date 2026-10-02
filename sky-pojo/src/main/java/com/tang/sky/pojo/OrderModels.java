package com.tang.sky.pojo;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
/** 订单保存下单时金额与收货快照；请求没有价格字段，客户端无法决定应付金额。 */
public final class OrderModels {
    private OrderModels() {}
    public enum Status { PENDING_PAYMENT, WAITING_ACCEPT, PREPARING, DELIVERING, COMPLETED, CANCELLED }
    public record CreateInput(@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{8,64}")String requestKey,@NotBlank @Size(max=300)String address,@NotBlank @Size(max=100)String contact) {}
    public record Order(Long id,String number,Long userId,String requestKey,String status,BigDecimal totalAmount,String address,String contact,LocalDateTime createdAt) {}
    public record Detail(Long id,Long orderId,String itemType,Long itemId,String name,BigDecimal unitPrice,int quantity,String flavor) {}
    public record View(Order order,List<Detail> details) {}
}
