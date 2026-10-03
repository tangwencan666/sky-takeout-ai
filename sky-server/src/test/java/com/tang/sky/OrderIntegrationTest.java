package com.tang.sky;
import com.tang.sky.common.*;
import com.tang.sky.pojo.AuthModels.*;
import com.tang.sky.pojo.CatalogModels.*;
import com.tang.sky.pojo.CartModels.*;
import com.tang.sky.pojo.OrderModels.*;
import com.tang.sky.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
/** 真实数据库验证订单快照、金额、幂等与隔离，全部测试数据回滚。 */
@SpringBootTest @Transactional @EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class OrderIntegrationTest {
    @Autowired OrderService orders;@Autowired CartService cart;@Autowired DishService dishes;@Autowired AuthService auth;
    @Autowired com.tang.sky.ai.tool.OrderTools orderTools;
    long user;long dish;
    @BeforeEach void setup(){String suffix=UUID.randomUUID().toString().replace("-", "").substring(0,16);user=auth.register(new Credentials("o"+suffix,"OrderTest!29")).id();UserContext.set(new UserContext.Identity(user,"USER"));long c=dishes.createCategory(new CategoryInput("订单菜",1,0));dish=dishes.create(new DishInput(c,"鸡肉",new BigDecimal("19.50"),"","",List.of()));dishes.status(dish,1);}
    @AfterEach void clean(){UserContext.clear();}
    @Test void checkoutSnapshotsIsolationAndReorder(){
        assertThrows(BusinessException.class,()->orders.create(new CreateInput("empty_order","地址","联系人")));
        cart.add(new AddInput("DISH",dish,"",2));var input=new CreateInput("request_001","地址","联系人");var order=orders.create(input);
        assertEquals(0,new BigDecimal("39.00").compareTo(order.order().totalAmount()));assertEquals(2,order.details().get(0).quantity());assertTrue(cart.view().items().isEmpty());
        assertEquals(order.order().id(),orders.create(input).order().id());assertEquals(1,orders.history(1,20).total());
        long other=auth.register(new Credentials("other_"+UUID.randomUUID().toString().replace("-", "").substring(0,16),"OtherTest!29")).id();
        UserContext.set(new UserContext.Identity(other,"USER"));assertThrows(BusinessException.class,()->orders.detail(order.order().id()));
        assertEquals(ErrorCode.NOT_FOUND,assertThrows(BusinessException.class,()->orders.cancel(order.order().id())).error());
        assertEquals(ErrorCode.NOT_FOUND,assertThrows(BusinessException.class,()->orders.again(order.order().id())).error());
        assertEquals(0,orders.history(1,20).total());assertTrue(cart.view().items().isEmpty());
        assertThrows(BusinessException.class,()->orderTools.getOrderDetail(order.order().id()));
        UserContext.set(new UserContext.Identity(user,"USER"));orders.cancel(order.order().id());assertEquals("CANCELLED",orders.detail(order.order().id()).order().status());orders.again(order.order().id());assertEquals(2,cart.view().items().get(0).quantity());
    }
    @Test void legalStateMachineAndUnavailableCart(){
        cart.add(new AddInput("DISH",dish,"",1));dishes.status(dish,0);assertThrows(BusinessException.class,()->orders.create(new CreateInput("unavailable","地址","联系人")));assertEquals(1,cart.view().items().size());dishes.status(dish,1);
        long id=orders.create(new CreateInput("state_test","地址","联系人")).order().id();
        UserContext.set(new UserContext.Identity(1,"ADMIN"));assertThrows(BusinessException.class,()->orders.advance(id,Status.COMPLETED));
        for(var state:List.of(Status.WAITING_ACCEPT,Status.PREPARING,Status.DELIVERING,Status.COMPLETED))orders.advance(id,state);
        assertEquals(ErrorCode.CONFLICT,assertThrows(BusinessException.class,()->orders.advance(id,Status.WAITING_ACCEPT)).error());
        UserContext.set(new UserContext.Identity(user,"USER"));assertEquals("COMPLETED",orders.detail(id).order().status());assertThrows(BusinessException.class,()->orders.cancel(id));
    }
    @Test void checkoutRepricesChangedDishInsteadOfUsingOldCartAmount() {
        cart.add(new AddInput("DISH",dish,"",2));assertEquals(new BigDecimal("39.00"),cart.view().totalPrice());
        var original=dishes.detail(dish,false).dish();
        dishes.update(dish,new DishInput(original.categoryId(),original.name(),new BigDecimal("21.75"),"","",List.of()));
        var order=orders.create(new CreateInput("reprice_test","地址","联系人"));
        assertEquals(new BigDecimal("43.50"),order.order().totalAmount());
        assertEquals(new BigDecimal("21.75"),order.details().get(0).unitPrice());
    }
}
