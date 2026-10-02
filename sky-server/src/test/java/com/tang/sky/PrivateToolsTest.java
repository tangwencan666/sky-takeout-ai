package com.tang.sky;
import com.tang.sky.ai.tool.*;
import com.tang.sky.common.*;
import com.tang.sky.service.*;
import com.tang.sky.pojo.CartModels.View;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.ai.support.ToolCallbacks;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 身份缺失时不能读私有数据；工具schema没有userId，模型无法选择目标用户。 */
class PrivateToolsTest {
    @AfterEach void clean(){UserContext.clear();}
    @Test void toolSchemaAndAuthorization(){
        var cart=mock(CartService.class);var tool=new CartTools(cart);
        assertThrows(BusinessException.class,tool::getShoppingCart);verifyNoInteractions(cart);
        UserContext.set(new UserContext.Identity(42,"USER"));when(cart.view()).thenReturn(new View(List.of(),BigDecimal.ZERO,false));assertEquals(BigDecimal.ZERO,tool.calculateCartPrice());
        for(var callback:ToolCallbacks.from(tool,new OrderTools(mock(OrderService.class))))assertFalse(callback.getToolDefinition().inputSchema().contains("userId"));
    }
}
