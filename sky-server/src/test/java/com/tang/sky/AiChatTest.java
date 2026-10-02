package com.tang.sky;
import com.tang.sky.ai.config.AiConfig;
import com.tang.sky.ai.model.ChatModels;
import com.tang.sky.ai.prompt.PromptCatalog;
import com.tang.sky.ai.service.AiAssistantService;
import com.tang.sky.common.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
/** 聊天入口要求已认证身份，保留/生成会话ID，不会让匿名用户直接消耗模型。 */
class AiChatTest {
    @AfterEach void cleanup(){UserContext.clear();}
    @Test void conversationAndIdentity()throws Exception{
        var service=new AiAssistantService(new AiConfig().chatModel("mock","","",""),new PromptCatalog(),"mock");
        assertThrows(BusinessException.class,()->service.chat(new ChatModels.Request("午餐",null)));
        UserContext.set(new UserContext.Identity(1,"USER"));var first=service.chat(new ChatModels.Request("想吃辣的",null));
        assertFalse(first.conversationId().isBlank());assertEquals("mock",first.mode());
        assertEquals(first.conversationId(),service.chat(new ChatModels.Request("30以内",first.conversationId())).conversationId());
    }
}
