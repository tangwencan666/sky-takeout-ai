package com.tang.sky;
import com.tang.sky.ai.config.MockChatModel;
import com.tang.sky.ai.model.ChatModels.Request;
import com.tang.sky.ai.prompt.PromptCatalog;
import com.tang.sky.ai.service.AiAssistantService;
import com.tang.sky.ai.tool.*;
import com.tang.sky.common.*;
import com.tang.sky.service.CartService;
import com.tang.sky.pojo.CartModels.View;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 流协议与异步身份传播离线测试；真实网络模型流尚需独立集成验证。 */
class StreamingTest {
    @AfterEach void cleanup(){UserContext.clear();}
    @Test void streamingAndCancel()throws Exception{
        UserContext.set(new UserContext.Identity(1,"USER"));var service=new AiAssistantService(new MockChatModel(),new PromptCatalog(),"mock");var chunks=new ArrayList<String>();
        var reply=service.streamChat(new Request("午餐","stream"),chunks::add,()->false);assertTrue(chunks.size()>1);assertEquals(reply.message(),String.join("",chunks));
        assertThrows(java.util.concurrent.CancellationException.class,()->service.streamChat(new Request("取消","cancel"),chunks::add,()->true));
    }
    @Test void contextIsNotModelInputAndIsCleared(){
        var cart=mock(CartService.class);when(cart.view()).thenAnswer(inv->{assertEquals(9,UserContext.userId());return new View(List.of(),BigDecimal.ZERO,false);});
        var callback=Arrays.stream(ToolCallbacks.from(new CartTools(cart))).filter(t->t.getToolDefinition().name().equals("getShoppingCart")).findFirst().orElseThrow();
        assertFalse(callback.getToolDefinition().inputSchema().contains("context"));
        callback.call("{}",new ToolContext(Map.of("identity",new UserContext.Identity(9,"USER"))));assertNull(UserContext.currentOrNull());
    }
}
