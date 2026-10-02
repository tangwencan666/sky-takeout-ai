package com.tang.sky;
import com.tang.sky.ai.tool.DishTools;
import com.tang.sky.pojo.CatalogModels.*;
import com.tang.sky.service.DishService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 直接测试工具及Spring AI JSON回调绑定，不依赖真实模型，也不让模型计算价格。 */
class DishToolsTest {
    @Test void serviceBoundaryAndJsonCallback(){
        var service=mock(DishService.class);var dish=new Dish(1L,1L,"鸡肉",new BigDecimal("20.00"),"微辣","",1);
        when(service.page(null,true,"鸡",1,100)).thenReturn(new PageView<>(1,1,100,List.of(dish)));
        when(service.page(null,true,null,1,20)).thenReturn(new PageView<>(1,1,20,List.of(dish)));
        var tools=new DishTools(service);assertEquals(0,tools.searchDish("鸡",new BigDecimal("10")).size());
        var callback=Arrays.stream(ToolCallbacks.from(tools)).filter(t->t.getToolDefinition().name().equals("getAvailableDishes")).findFirst().orElseThrow();
        String json=callback.call("{}");assertTrue(json.contains("鸡肉"));assertTrue(json.contains("20"));
        verify(service).page(null,true,null,1,20);assertFalse(callback.getToolDefinition().inputSchema().contains("userId"));
    }
}
