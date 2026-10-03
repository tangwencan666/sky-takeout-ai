package com.tang.sky.ai.tool;
import com.tang.sky.common.*;
import com.tang.sky.pojo.CartModels.View;
import com.tang.sky.service.CartService;
import java.math.BigDecimal;
import org.slf4j.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
/** 只读购物车工具不提供加购、下单或支付；金额仍由Java业务层计算。 */
@Component
public class CartTools {
    private static final Logger log=LoggerFactory.getLogger(CartTools.class);
    private final CartService cart;public CartTools(CartService cart){this.cart=cart;}
    @Tool(description="用户询问我选了什么、购物车有哪些商品时调用。无需参数，读取JWT当前用户的商品、数量、后端重算金额和是否可结算；停售商品会标记不可结算。不能读取其他账号，不执行加购或清空。")
    public View getShoppingCart(org.springframework.ai.chat.model.ToolContext context){return ToolIdentity.run(context,this::getShoppingCart);}
    public View getShoppingCart(){if(!UserContext.require().role().equals("USER"))throw new BusinessException(ErrorCode.FORBIDDEN);log.info("Tool getShoppingCart userId={}",UserContext.userId());return cart.view();}
    @Tool(description="用户询问购物车现在多少钱、是否超预算时调用。无需参数，返回JWT当前用户购物车按数据库现价计算的人民币总金额；存在不可售商品时拒绝估算，不代表支付或已创建订单。")
    public BigDecimal calculateCartPrice(org.springframework.ai.chat.model.ToolContext context){return ToolIdentity.run(context,this::calculateCartPrice);}
    public BigDecimal calculateCartPrice(){var view=getShoppingCart();if(!view.checkoutAllowed()&&!view.items().isEmpty())throw new BusinessException(ErrorCode.CONFLICT,"购物车包含不可售商品");return view.totalPrice();}
}
