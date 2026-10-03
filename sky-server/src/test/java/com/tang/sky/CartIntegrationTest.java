package com.tang.sky;
import com.tang.sky.common.*;
import com.tang.sky.pojo.AuthModels.*;
import com.tang.sky.pojo.CartModels.*;
import com.tang.sky.pojo.CatalogModels.*;
import com.tang.sky.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
/** 两个真实用户切换上下文，验证购物车归属过滤和后端核价。 */
@SpringBootTest @Transactional @EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class CartIntegrationTest {
    @Autowired CartService cart;@Autowired DishService dishes;@Autowired AuthService auth;
    @AfterEach void clean(){UserContext.clear();}
    @Test void mergePricingAndIsolation(){
        String suffix=UUID.randomUUID().toString().replace("-", "").substring(0,15);
        var a=auth.register(new Credentials("ca"+suffix,"CartTest!29"));var b=auth.register(new Credentials("cb"+suffix,"CartTest!29"));
        long category=dishes.createCategory(new CategoryInput("购物车测试",1,0));long dish=dishes.create(new DishInput(category,"鸡肉",new BigDecimal("18.50"),"","",List.of()));dishes.status(dish,1);
        UserContext.set(new UserContext.Identity(a.id(),"USER"));var add=new AddInput("DISH",dish,"微辣",1);cart.add(add);cart.add(add);
        var view=cart.view();assertEquals(1,view.items().size());assertEquals(2,view.items().get(0).quantity());assertEquals(0,new BigDecimal("37.00").compareTo(view.totalPrice()));long item=view.items().get(0).id();
        UserContext.set(new UserContext.Identity(b.id(),"USER"));assertTrue(cart.view().items().isEmpty());assertThrows(BusinessException.class,()->cart.delete(item));
        assertEquals(ErrorCode.NOT_FOUND,assertThrows(BusinessException.class,()->cart.reduce(item)).error());
        cart.clear();
        UserContext.set(new UserContext.Identity(a.id(),"USER"));cart.reduce(item);assertEquals(1,cart.view().items().get(0).quantity());
        dishes.status(dish,0);assertFalse(cart.view().checkoutAllowed());assertNull(cart.view().items().get(0).unitPrice());
        cart.clear();assertTrue(cart.view().items().isEmpty());
    }
}
