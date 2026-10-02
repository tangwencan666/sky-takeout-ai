package com.tang.sky.service.impl;
import com.tang.sky.common.*;
import com.tang.sky.mapper.*;
import com.tang.sky.pojo.OrderModels.*;
import com.tang.sky.pojo.CartModels.AddInput;
import com.tang.sky.pojo.CatalogModels.PageView;
import com.tang.sky.service.*;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 下单、明细快照、清空购物车同事务；锁用户行协调并发购物车操作，幂等键防重复订单。 */
@Service
public class OrderServiceImpl implements OrderService {
    private static final Logger log=LoggerFactory.getLogger(OrderServiceImpl.class);
    private final OrderMapper mapper;private final CartMapper carts;private final CartService cart;
    public OrderServiceImpl(OrderMapper mapper,CartMapper carts,CartService cart){this.mapper=mapper;this.carts=carts;this.cart=cart;}
    @Transactional public View create(CreateInput input){
        long user=UserContext.userId();if(carts.lockUser(user)==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);
        var existing=mapper.byKey(user,input.requestKey());if(existing!=null)return new View(existing,mapper.details(existing.id()));
        var current=cart.view();if(current.items().isEmpty())throw new BusinessException(ErrorCode.CONFLICT,"购物车为空");
        if(!current.checkoutAllowed())throw new BusinessException(ErrorCode.CONFLICT,"购物车包含不可售商品");
        // view通过业务Service重新读取当前数据库价格，此处不接收客户端总价。
        mapper.insert(UUID.randomUUID().toString(),user,input,current.totalPrice());long id=mapper.lastId();
        for(var line:current.items())mapper.line(id,line);
        carts.clear(user);log.info("Order created orderId={} userId={}",id,user);return detail(id);
    }
    public View detail(long id){var order=mapper.owned(id,UserContext.userId());if(order==null)throw new BusinessException(ErrorCode.NOT_FOUND,"订单不存在");return new View(order,mapper.details(id));}
    public PageView<Order> history(int page,int size){if(page<1||page>100000||size<1||size>100)throw new BusinessException(ErrorCode.BAD_REQUEST);long user=UserContext.userId();return new PageView<>(mapper.count(user),page,size,mapper.history(user,(page-1)*size,size));}
    @Transactional public void cancel(long id){
        carts.lockUser(UserContext.userId());var order=detail(id).order();
        if(!order.status().equals("PENDING_PAYMENT")&&!order.status().equals("WAITING_ACCEPT"))throw new BusinessException(ErrorCode.CONFLICT,"当前状态不允许取消");
        change(order,Status.CANCELLED);log.info("Order cancelled orderId={} userId={}",id,UserContext.userId());
    }
    @Transactional public void again(long id){var order=detail(id);for(var line:order.details())cart.add(new AddInput(line.itemType(),line.itemId(),line.flavor(),line.quantity()));}
    @Transactional public void advance(long id,Status next){
        if(!UserContext.require().role().equals("ADMIN"))throw new BusinessException(ErrorCode.FORBIDDEN);
        var order=mapper.adminFind(id);if(order==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        // 本教学版只支持管理员模拟确认收款后的顺序流转，不调用支付服务、不发生真实扣款。
        String expected=switch(Status.valueOf(order.status())){
            case PENDING_PAYMENT->"WAITING_ACCEPT";case WAITING_ACCEPT->"PREPARING";case PREPARING->"DELIVERING";case DELIVERING->"COMPLETED";default->"";};
        if(!expected.equals(next.name()))throw new BusinessException(ErrorCode.CONFLICT,"非法订单状态流转");change(order,next);
    }
    private void change(Order order,Status next){if(mapper.transition(order.id(),order.status(),next.name())!=1)throw new BusinessException(ErrorCode.CONFLICT,"订单状态已变化，请刷新");}
}
