package com.tang.sky.ai.tool;
import com.tang.sky.common.*;
import com.tang.sky.service.OrderService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
/** 工具不接收userId；身份来自JWT上下文，返回模型前去掉地址、联系人、幂等键等非必要数据。 */
@Component
public class OrderTools {
    public record Summary(Long id,String status,BigDecimal totalPrice,LocalDateTime createdAt){}
    public record Item(String name,BigDecimal unitPrice,int quantity){}
    public record Detail(Summary order,List<Item> items){}
    private static final Logger log=LoggerFactory.getLogger(OrderTools.class);
    private final OrderService orders;public OrderTools(OrderService orders){this.orders=orders;}
    private void authorize(){if(!UserContext.require().role().equals("USER"))throw new BusinessException(ErrorCode.FORBIDDEN);}
    @Tool(description="用户询问我的历史订单、最近订单状态时调用。无需模型传身份，返回JWT当前用户最近10笔订单的ID、状态、金额和创建时间；不包含地址、联系人，不接受其他用户ID。")
    public List<Summary> getRecentOrders(org.springframework.ai.chat.model.ToolContext context){return ToolIdentity.run(context,this::getRecentOrders);}
    public List<Summary> getRecentOrders(){authorize();log.info("Tool getRecentOrders userId={}",UserContext.userId());return orders.history(1,10).records().stream().map(o->new Summary(o.id(),o.status(),o.totalAmount(),o.createdAt())).toList();}
    @Tool(description="用户询问自己某笔订单包含哪些餐品或金额时调用。orderId来自本人的订单列表；返回订单摘要和餐品名称、单价、数量。只允许JWT当前用户自己的订单，其他用户订单返回不存在，不执行取消或修改。")
    public Detail getOrderDetail(@ToolParam(description="当前用户订单列表中的订单ID，不能传其他用户身份")long orderId,org.springframework.ai.chat.model.ToolContext context){return ToolIdentity.run(context,()->getOrderDetail(orderId));}
    public Detail getOrderDetail(long orderId){authorize();var view=orders.detail(orderId);var o=view.order();log.info("Tool getOrderDetail userId={} orderId={}",UserContext.userId(),orderId);return new Detail(new Summary(o.id(),o.status(),o.totalAmount(),o.createdAt()),view.details().stream().map(d->new Item(d.name(),d.unitPrice(),d.quantity())).toList());}
}
