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
    @Tool(description="读取当前登录用户购物车，不接受其他用户ID")
    public View getShoppingCart(org.springframework.ai.chat.model.ToolContext context){return ToolIdentity.run(context,this::getShoppingCart);}
    public View getShoppingCart(){if(!UserContext.require().role().equals("USER"))throw new BusinessException(ErrorCode.FORBIDDEN);log.info("Tool getShoppingCart userId={}",UserContext.userId());return cart.view();}
    @Tool(description="计算当前用户购物车真实总价；存在不可售商品时拒绝估算为可结算金额")
    public BigDecimal calculateCartPrice(org.springframework.ai.chat.model.ToolContext context){return ToolIdentity.run(context,this::calculateCartPrice);}
    public BigDecimal calculateCartPrice(){var view=getShoppingCart();if(!view.checkoutAllowed()&&!view.items().isEmpty())throw new BusinessException(ErrorCode.CONFLICT,"购物车包含不可售商品");return view.totalPrice();}
}
