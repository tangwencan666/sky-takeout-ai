package com.tang.sky;
import com.tang.sky.ai.memory.ConversationMemory;
import com.tang.sky.ai.prompt.PromptCatalog;
import com.tang.sky.ai.service.AiAssistantService;
import com.tang.sky.ai.model.ChatModels.Request;
import com.tang.sky.common.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
/** 确认历史真的进入模型请求，而不只是原样返回conversationId。 */
class MemoryTest {
    @AfterEach void cleanup(){UserContext.clear();}
    @Test void historyReachesChatModelAndUsersAreIsolated()throws Exception{
        var seen=new AtomicReference<String>();ChatModel model=prompt->{seen.set(prompt.getInstructions().toString());return new ChatResponse(List.of(new Generation(new AssistantMessage("测试回复"))));};
        var service=new AiAssistantService(model,new PromptCatalog(),"mock");UserContext.set(new UserContext.Identity(1,"USER"));
        service.chat(new Request("我想吃辣的","same"));service.chat(new Request("30块以内","same"));assertTrue(seen.get().contains("我想吃辣的"));assertTrue(seen.get().contains("30块以内"));
        service.chat(new Request("新的会话","other"));assertFalse(seen.get().contains("我想吃辣的"));assertFalse(seen.get().contains("30块以内"));
        UserContext.set(new UserContext.Identity(2,"USER"));service.chat(new Request("午餐","same"));assertFalse(seen.get().contains("我想吃辣的"));
    }
    @Test void boundedHistoryAndFailureDoesNotPersist(){
        var memory=new ConversationMemory();for(int i=0;i<15;i++)memory.exchange(1,"c","输入",messages->{assertTrue(messages.size()<=21);return "答复";});
        assertThrows(IllegalStateException.class,()->memory.exchange(1,"new","失败",messages->{throw new IllegalStateException();}));
        memory.exchange(1,"new","再次",messages->{assertEquals(1,messages.size());return "成功";});
    }
}
