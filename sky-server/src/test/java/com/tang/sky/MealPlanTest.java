package com.tang.sky;
import com.tang.sky.ai.config.AiConfig;
import com.tang.sky.ai.model.MealPlanModels.*;
import com.tang.sky.ai.prompt.PromptCatalog;
import com.tang.sky.ai.service.MealPlanService;
import com.tang.sky.ai.tool.DishTools;
import com.tang.sky.common.*;
import com.tang.sky.pojo.CatalogModels.*;
import com.tang.sky.service.DishService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 验证真实Java金额重算和模型草案拒绝路径，不调用收费API。 */
class MealPlanTest {
    @AfterEach void cleanup(){UserContext.clear();}
    @Test void validatesPriceIdsAndBudget()throws Exception{
        var dishes=mock(DishService.class);var dish=new Dish(1L,1L,"鸡肉",new BigDecimal("25.00"),"","",1);
        when(dishes.detail(1,true)).thenReturn(new DishView(dish,List.of()));when(dishes.page(null,true,null,1,20)).thenReturn(new PageView<>(1,1,20,List.of(dish)));
        var service=new MealPlanService(new AiConfig().chatModel("mock","","",""),new DishTools(dishes),dishes,new PromptCatalog(),new ObjectMapper(),"mock");
        assertEquals(0,new BigDecimal("25.00").compareTo(service.validate(new Draft("理由",List.of(1L)),new BigDecimal("30"),Set.of(1L)).totalPrice()));
        assertThrows(BusinessException.class,()->service.validate(new Draft("理由",List.of(1L)),new BigDecimal("20"),Set.of(1L)));
        assertThrows(BusinessException.class,()->service.validate(new Draft("理由",List.of(1L,1L)),new BigDecimal("80"),Set.of(1L)));
        assertThrows(BusinessException.class,()->service.validate(new Draft("理由",List.of(99L)),new BigDecimal("80"),Set.of(1L)));
        UserContext.set(new UserContext.Identity(1,"USER"));assertEquals("mock",service.create(new Request("预算30",null)).mode());
        assertThrows(BusinessException.class,()->service.create(new Request("随便",null)));
    }
    @Test void structuredOutputConvertsToJavaRecord(){
        var converter=new org.springframework.ai.converter.BeanOutputConverter<>(Draft.class);
        var draft=converter.convert("{\"reason\":\"口味匹配\",\"dishIds\":[1,2]}");assertEquals(List.of(1L,2L),draft.dishIds());assertTrue(converter.getFormat().contains("dishIds"));
    }
}
