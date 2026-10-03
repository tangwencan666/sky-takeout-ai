package com.tang.sky;

import com.tang.sky.common.UserContext;
import com.tang.sky.mapper.OrderMapper;
import com.tang.sky.pojo.AuthModels.Credentials;
import com.tang.sky.pojo.CartModels.*;
import com.tang.sky.pojo.CatalogModels.*;
import com.tang.sky.pojo.OrderModels.CreateInput;
import com.tang.sky.service.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 不使用测试外层事务：在真实SQL约束失败后重新查库，证明业务自己的事务完成回滚。 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class BusinessTransactionIntegrationTest {
    @Autowired DishService dishes;
    @Autowired AuthService auth;
    @Autowired CartService cart;
    @Autowired OrderService orders;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @MockitoSpyBean OrderMapper mapper;
    long user,category,dish;

    @BeforeEach void verifyDatabaseAndCreateFixture() throws Exception {
        try(var connection=dataSource.getConnection()) { assertEquals("sky_takeout_ai",connection.getCatalog()); }
        category=dishes.createCategory(new CategoryInput("回滚验收_"+UUID.randomUUID().toString().substring(0,8),1,0));
    }

    @AfterEach void removeOnlyOwnFixtures() {
        UserContext.clear();
        if(user!=0) {
            jdbc.update("DELETE d FROM order_detail d JOIN orders o ON o.id=d.order_id WHERE o.user_id=?",user);
            jdbc.update("DELETE FROM orders WHERE user_id=?",user);
            jdbc.update("DELETE FROM shopping_cart WHERE user_id=?",user);
            jdbc.update("DELETE FROM `user` WHERE id=?",user);
        }
        if(dish!=0) { dishes.status(dish,0);dishes.delete(dish); }
        if(category!=0) dishes.deleteCategory(category);
    }

    @Test void secondDetailSqlFailureRollsBackHeaderFirstDetailAndPreservesCart() {
        String suffix=UUID.randomUUID().toString().replace("-", "").substring(0,16);
        user=auth.register(new Credentials("rollback_"+suffix,"RollbackTest!29")).id();
        UserContext.set(new UserContext.Identity(user,"USER"));
        dish=dishes.create(new DishInput(category,"事务菜品",new BigDecimal("13.50"),"","",List.of()));
        dishes.status(dish,1);
        cart.add(new AddInput("DISH",dish,"微辣",1));cart.add(new AddInput("DISH",dish,"中辣",2));
        var writes=new AtomicInteger();var attemptedOrder=new AtomicLong();
        // 第二次明细写入故意触发MySQL NOT NULL约束，第一行及订单头已在同一事务中写入。
        doAnswer(invocation->{
            long orderId=invocation.getArgument(0);Line line=invocation.getArgument(1);attemptedOrder.set(orderId);
            BigDecimal price=writes.incrementAndGet()==2?null:line.unitPrice();
            return jdbc.update("INSERT INTO order_detail(order_id,item_type,item_id,name,unit_price,quantity,flavor) VALUES(?,?,?,?,?,?,?)",
                    orderId,line.itemType(),line.itemId(),line.name(),price,line.quantity(),line.flavor());
        }).when(mapper).line(anyLong(),any(Line.class));
        assertThrows(DataIntegrityViolationException.class,()->orders.create(new CreateInput("rollback_"+suffix,"测试地址","测试联系人")));
        assertEquals(2,writes.get());assertTrue(attemptedOrder.get()>0);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?",Integer.class,user));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM order_detail WHERE order_id=?",Integer.class,attemptedOrder.get()));
        assertEquals(2,cart.view().items().size());assertEquals(new BigDecimal("40.50"),cart.view().totalPrice());
    }

    @Test void invalidSecondFlavorRollsBackDishAndFirstFlavor() {
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM dish_flavor",Long.class);
        var input=new DishInput(category,"失败菜品",new BigDecimal("10.00"),"","",
                List.of(new FlavorInput("辣度","微辣"),new FlavorInput("米饭",null)));
        assertThrows(DataIntegrityViolationException.class,()->dishes.create(input));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dish WHERE category_id=?",Integer.class,category));
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM dish_flavor",Long.class));
    }
}
