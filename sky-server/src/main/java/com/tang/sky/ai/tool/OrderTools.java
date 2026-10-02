package com.tang.sky.ai.tool;
import com.tang.sky.common.*;
import com.tang.sky.service.OrderService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.*;
import org.springframework.ai.tool.annotation.Tool;
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
    @Tool(description="查询当前登录用户最近10笔订单，不接受其他用户ID")
    public List<Summary> getRecentOrders(){authorize();log.info("Tool getRecentOrders userId={}",UserContext.userId());return orders.history(1,10).records().stream().map(o->new Summary(o.id(),o.status(),o.totalAmount(),o.createdAt())).toList();}
    @Tool(description="查询当前登录用户的一笔订单，仅允许属于当前用户的orderId")
    public Detail getOrderDetail(long orderId){authorize();var view=orders.detail(orderId);var o=view.order();log.info("Tool getOrderDetail userId={} orderId={}",UserContext.userId(),orderId);return new Detail(new Summary(o.id(),o.status(),o.totalAmount(),o.createdAt()),view.details().stream().map(d->new Item(d.name(),d.unitPrice(),d.quantity())).toList());}
}
