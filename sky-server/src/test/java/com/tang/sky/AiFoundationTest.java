package com.tang.sky;
import com.tang.sky.ai.config.AiConfig;
import com.tang.sky.ai.prompt.PromptCatalog;
import com.tang.sky.ai.service.AiAssistantService;
import com.tang.sky.common.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** 不加载收费模型：用Mock走真实ChatClient，验证提示词和错误隔离。 */
class AiFoundationTest {
    @Test void mockUsesChatClientAndLabelsOutput() throws Exception {
        var prompts=new PromptCatalog();var model=new AiConfig().chatModel("mock","","","");
        assertTrue(new AiAssistantService(model,prompts,"mock").reply("推荐午餐").contains("Mock"));
        assertTrue(prompts.ordering().contains("禁止编造"));assertTrue(prompts.ordering().contains("查询"));
    }
    @Test void missingKeyDoesNotBreakConstruction() throws Exception {
        var model=new AiConfig().chatModel("live","","","");
        assertEquals(ErrorCode.AI_UNAVAILABLE,assertThrows(BusinessException.class,()->new AiAssistantService(model,new PromptCatalog(),"live").reply("午餐")).error());
    }
}
