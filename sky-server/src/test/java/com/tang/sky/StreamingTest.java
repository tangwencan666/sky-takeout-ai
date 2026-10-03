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
import java.time.Duration;
import java.util.concurrent.atomic.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
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
    @Test void cancelAfterFirstChunkClosesUpstreamWithoutWaitingForNextChunk()throws Exception{
        var cancelled=new AtomicBoolean();var upstreamCancelled=new AtomicBoolean();
        var service=new AiAssistantService(model(Flux.concat(Flux.just(response("partial")),Flux.never()).doOnCancel(()->upstreamCancelled.set(true))),new PromptCatalog(),"mock");
        assertTimeoutPreemptively(Duration.ofSeconds(3),()->{
            UserContext.set(new UserContext.Identity(1,"USER"));
            try{assertThrows(java.util.concurrent.CancellationException.class,()->service.streamChat(new Request("取消","cancel-between"),chunk->cancelled.set(true),cancelled::get));}
            finally{UserContext.clear();}
        });
        assertTrue(upstreamCancelled.get(),"取消必须传播给模型上游订阅");
    }
    @Test void idleTimeoutCancelsUpstream()throws Exception{
        var cancelled=new AtomicBoolean();
        var service=new AiAssistantService(model(Flux.<ChatResponse>never().doOnCancel(()->cancelled.set(true))),new PromptCatalog(),"mock");
        ReflectionTestUtils.setField(service,"streamIdleTimeoutMs",50L);
        assertTimeoutPreemptively(Duration.ofSeconds(3),()->{
            UserContext.set(new UserContext.Identity(1,"USER"));
            try{assertThrows(RuntimeException.class,()->service.streamChat(new Request("超时","idle"),chunk->fail("超时流不能产生delta"),()->false));}
            finally{UserContext.clear();}
        });
        assertTrue(cancelled.get());
    }
    @Test void modelFailureDoesNotSavePartialMemoryAndRetryCanUseSameConversation()throws Exception{
        var seen=new AtomicReference<Prompt>();var attempts=new AtomicInteger();
        ChatModel model=new ChatModel(){
            public ChatResponse call(Prompt prompt){throw new AssertionError("expected stream");}
            public Flux<ChatResponse> stream(Prompt prompt){seen.set(prompt);return attempts.getAndIncrement()==0?Flux.concat(Flux.just(response("partial")),Flux.error(new IllegalStateException("simulated"))):Flux.just(response("complete"));}
        };
        var service=new AiAssistantService(model,new PromptCatalog(),"mock");UserContext.set(new UserContext.Identity(1,"USER"));
        assertThrows(RuntimeException.class,()->service.streamChat(new Request("failed-first-message","retry"),chunk->{},()->false));
        assertEquals("complete",service.streamChat(new Request("retry-message","retry"),chunk->{},()->false).message());
        assertTrue(seen.get().getInstructions().stream().noneMatch(m->m.getText().contains("failed-first-message")||m.getText().contains("partial")));
    }
    @Test void disconnectedWriterClosesUpstream()throws Exception{
        var cancelled=new AtomicBoolean();
        var service=new AiAssistantService(model(Flux.concat(Flux.just(response("first")),Flux.never()).doOnCancel(()->cancelled.set(true))),new PromptCatalog(),"mock");
        UserContext.set(new UserContext.Identity(1,"USER"));
        assertThrows(java.io.UncheckedIOException.class,()->service.streamChat(new Request("断开","disconnect"),chunk->{throw new java.io.UncheckedIOException(new java.io.IOException("closed"));},()->false));
        assertTrue(cancelled.get());
    }
    private static ChatResponse response(String text){return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));}
    private static ChatModel model(Flux<ChatResponse> stream){return new ChatModel(){public ChatResponse call(Prompt p){throw new AssertionError("expected stream");}public Flux<ChatResponse> stream(Prompt p){return stream;}};}
}
