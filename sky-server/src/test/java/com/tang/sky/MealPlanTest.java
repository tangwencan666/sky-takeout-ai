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
import java.util.concurrent.atomic.AtomicInteger;
import com.tang.sky.ai.model.MealPlanRejectedException;
import com.tang.sky.ai.security.AiRequestAudit;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
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
    @Test void liveCreateUsesJsonObjectOnceWithoutToolsAndRecalculatesCurrentPrices()throws Exception{
        var dishes=mock(DishService.class);
        var candidateA=new Dish(1L,1L,"测试辣鸡",new BigDecimal("25.00"),"微辣","",1);
        var candidateB=new Dish(2L,1L,"测试米饭",new BigDecimal("3.00"),"主食","",1);
        var latestA=new Dish(1L,1L,"测试辣鸡",new BigDecimal("26.50"),"微辣","",1);
        when(dishes.page(null,true,null,1,20)).thenReturn(new PageView<>(2,1,20,List.of(candidateA,candidateB)));
        when(dishes.detail(1,true)).thenReturn(new DishView(latestA,List.of()));
        when(dishes.detail(2,true)).thenReturn(new DishView(candidateB,List.of()));
        var calls=new AtomicInteger();
        ChatModel localStub=prompt->{
            calls.incrementAndGet();String system=prompt.getInstructions().get(0).getText();
            assertTrue(system.contains("预先查询的真实候选菜单"));assertTrue(system.contains("JSON 结构示例"));
            assertTrue(system.contains("\"uniqueItems\":true"));assertTrue(system.contains("\"maxItems\":10"));
            assertTrue(system.contains("只返回一个配餐方案"));assertTrue(system.contains("不能用重复 ID 表示两份米饭"));
            assertTrue(system.contains("在 reason 中明确说明每道一份及人数取舍"));
            assertFalse(system.contains("菜单事实必须来自系统工具"));
            assertTrue(prompt.getInstructions().stream().anyMatch(m->m.getText().contains("CANDIDATE_MENU_JSON")&&m.getText().contains("测试辣鸡")));
            var options=(OpenAiChatOptions)prompt.getOptions();
            assertEquals(ResponseFormat.Type.JSON_OBJECT,options.getResponseFormat().getType());
            assertEquals(0.0,options.getTemperature());
            assertFalse(options.getInternalToolExecutionEnabled());assertTrue(options.getToolCallbacks()==null||options.getToolCallbacks().isEmpty());
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"reason\":\"按真实候选选择辣菜和主食\",\"dishIds\":[1,2]}"))));
        };
        var service=new MealPlanService(localStub,new DishTools(dishes),dishes,new PromptCatalog(),new ObjectMapper(),"live");
        UserContext.set(new UserContext.Identity(1,"USER"));
        var plan=service.create(new Request("两个人预算80，想吃辣一点",new BigDecimal("80")));
        assertEquals(1,calls.get());assertEquals("live",plan.mode());assertEquals(List.of(1L,2L),plan.dishIds());
        assertEquals(0,new BigDecimal("29.50").compareTo(plan.totalPrice()));
        verify(dishes).detail(1,true);verify(dishes).detail(2,true);
    }
    @Test void liveEmptySelectionLogsExactBusinessReasonWithoutPromptOrDraft()throws Exception{
        var dishes=mock(DishService.class);var dish=new Dish(1L,1L,"测试菜",new BigDecimal("10.00"),"","",1);
        when(dishes.page(null,true,null,1,20)).thenReturn(new PageView<>(1,1,20,List.of(dish)));
        ChatModel localStub=prompt->new ChatResponse(List.of(new Generation(new AssistantMessage("{\"reason\":\"private-model-text\",\"dishIds\":[]}"))));
        var service=new MealPlanService(localStub,new DishTools(dishes),dishes,new PromptCatalog(),new ObjectMapper(),"live");
        Logger logger=(Logger)LoggerFactory.getLogger(AiRequestAudit.class);var appender=new ListAppender<ILoggingEvent>();appender.start();logger.addAppender(appender);
        try{
            UserContext.set(new UserContext.Identity(1,"USER"));
            var failure=assertThrows(MealPlanRejectedException.class,()->service.create(new Request("private-user-text",new BigDecimal("80"))));
            assertEquals(MealPlanRejectedException.Reason.SELECTION_EMPTY,failure.reason());assertEquals(ErrorCode.CONFLICT,failure.error());
            var lines=appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertTrue(lines.stream().anyMatch(line->line.contains("category=MEAL_PLAN_SELECTION_EMPTY")));
            assertTrue(lines.stream().anyMatch(line->line.contains("dishCount=0")&&line.contains("budget=80")));
            assertTrue(lines.stream().noneMatch(line->line.contains("private-model-text")||line.contains("private-user-text")||line.contains("category=ADAPTER")));
        }finally{logger.detachAppender(appender);appender.stop();}
    }
    @Test void liveCreateRejectsPriceIncreaseOverBudgetWithoutAnotherModelAttempt()throws Exception{
        var dishes=mock(DishService.class);var candidate=new Dish(1L,1L,"测试菜",new BigDecimal("20.00"),"","",1);
        when(dishes.page(null,true,null,1,20)).thenReturn(new PageView<>(1,1,20,List.of(candidate)));
        when(dishes.detail(1,true)).thenReturn(new DishView(new Dish(1L,1L,"测试菜",new BigDecimal("35.00"),"","",1),List.of()));
        var calls=new AtomicInteger();ChatModel localStub=prompt->{calls.incrementAndGet();return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"reason\":\"按候选价格选择\",\"dishIds\":[1]}"))));};
        var service=new MealPlanService(localStub,new DishTools(dishes),dishes,new PromptCatalog(),new ObjectMapper(),"live");
        UserContext.set(new UserContext.Identity(1,"USER"));
        var failure=assertThrows(MealPlanRejectedException.class,()->service.create(new Request("预算30",new BigDecimal("30"))));
        assertEquals(MealPlanRejectedException.Reason.OVER_BUDGET,failure.reason());assertEquals(1,calls.get());verify(dishes).detail(1,true);
    }
    @Test void liveDuplicateIdsAreRejectedWithoutDeduplicationOrPaidRetry()throws Exception{
        var dishes=mock(DishService.class);var dish=new Dish(1L,1L,"测试米饭",new BigDecimal("3.00"),"主食","",1);
        when(dishes.page(null,true,null,1,20)).thenReturn(new PageView<>(1,1,20,List.of(dish)));
        when(dishes.detail(1,true)).thenReturn(new DishView(dish,List.of()));
        var calls=new AtomicInteger();ChatModel localStub=prompt->{calls.incrementAndGet();return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"reason\":\"两份主食\",\"dishIds\":[1,1]}"))));};
        var service=new MealPlanService(localStub,new DishTools(dishes),dishes,new PromptCatalog(),new ObjectMapper(),"live");
        UserContext.set(new UserContext.Identity(1,"USER"));
        var failure=assertThrows(MealPlanRejectedException.class,()->service.create(new Request("两个人预算80",new BigDecimal("80"))));
        assertEquals(MealPlanRejectedException.Reason.DISH_ID_DUPLICATE,failure.reason());
        assertEquals(ErrorCode.CONFLICT,failure.error());assertEquals(1,calls.get());verify(dishes,times(1)).detail(1,true);
    }
}
