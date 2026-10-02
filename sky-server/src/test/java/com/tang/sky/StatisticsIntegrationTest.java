package com.tang.sky;

import com.tang.sky.common.*;
import com.tang.sky.pojo.AuthModels.*;
import com.tang.sky.pojo.CatalogModels.*;
import com.tang.sky.pojo.CartModels.*;
import com.tang.sky.pojo.OrderModels.*;
import com.tang.sky.service.*;
import com.tang.sky.ai.service.AdminAiService;
import com.tang.sky.ai.tool.StatisticsTools;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional @EnabledIfEnvironmentVariable(named="RUN_DB_TESTS", matches="true")
class StatisticsIntegrationTest {
    @Autowired StatisticsService statistics;
    @Autowired StatisticsTools tools;
    @Autowired AdminAiService ai;
    @Autowired OrderService orders;
    @Autowired CartService cart;
    @Autowired DishService dishes;
    @Autowired AuthService auth;
    @AfterEach void cleanup() { UserContext.clear(); }
    @Test void aggregatesCompletedOrdersOnlyAndZeroFillsDates() {
        UserContext.set(new UserContext.Identity(1, "ADMIN"));
        var before = statistics.revenue(7);
        long countBefore = statistics.trend(7).stream().mapToLong(t -> t.orderCount()).sum();
        long user = auth.register(new Credentials("s" + UUID.randomUUID().toString().replace("-", "").substring(0,20), "StatsTest!29")).id();
        long category = dishes.createCategory(new CategoryInput("统计菜品", 1, 0));
        long dish = dishes.create(new DishInput(category, "统计单品", new BigDecimal("12.50"), "", "", List.of()));
        dishes.status(dish, 1);
        UserContext.set(new UserContext.Identity(user, "USER"));
        cart.add(new AddInput("DISH", dish, "", 2));
        long id = orders.create(new CreateInput("stats_request", "测试地址", "测试联系人")).order().id();
        UserContext.set(new UserContext.Identity(1, "ADMIN"));
        assertEquals(before, statistics.revenue(7));
        for (var state : List.of(Status.WAITING_ACCEPT, Status.PREPARING, Status.DELIVERING, Status.COMPLETED)) orders.advance(id, state);
        assertEquals(before.completedOrders() + 1, statistics.revenue(7).completedOrders());
        assertEquals(0, before.totalAmount().add(new BigDecimal("25.00")).compareTo(statistics.revenue(7).totalAmount()));
        assertEquals(7, statistics.trend(7).size());
        assertEquals(countBefore + 1, statistics.trend(7).stream().mapToLong(t -> t.orderCount()).sum());
        assertTrue(statistics.top(7).stream().anyMatch(d -> d.dishId() == dish && d.quantity() == 2));
        assertTrue(ai.analyze("分析", 7).answer().contains("Mock"));
    }
    @Test void rolesAndRangeAreCheckedInsideServiceAndTool() {
        UserContext.set(new UserContext.Identity(1, "USER"));
        assertThrows(BusinessException.class, () -> statistics.revenue(7));
        assertThrows(BusinessException.class, () -> tools.getRevenueStatistics(7, null));
        UserContext.set(new UserContext.Identity(1, "ADMIN"));
        assertThrows(BusinessException.class, () -> statistics.revenue(91));
        assertThrows(BusinessException.class, () -> statistics.trend(0));
        for (var callback : org.springframework.ai.support.ToolCallbacks.from(tools)) {
            assertFalse(callback.getToolDefinition().inputSchema().contains("userId"));
            assertFalse(callback.getToolDefinition().inputSchema().contains("context"));
        }
    }
}
