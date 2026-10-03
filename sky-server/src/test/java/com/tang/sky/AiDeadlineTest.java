package com.tang.sky;

import com.tang.sky.ai.config.DeadlineChatModel;
import com.tang.sky.ai.security.*;
import com.tang.sky.common.UserContext;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import static org.junit.jupiter.api.Assertions.*;

class AiDeadlineTest {
    @AfterEach void cleanup(){UserContext.clear();}
    @Test void totalDeadlineInterruptsModelAndDoesNotRetainWorkerIdentity()throws Exception {
        var interrupted=new CountDownLatch(1);
        ChatModel blocked=prompt->{
            assertEquals(7,UserContext.userId());
            try{Thread.sleep(10000);throw new AssertionError("应被取消");}
            catch(InterruptedException e){interrupted.countDown();throw new RuntimeException(e);}
        };
        UserContext.set(new UserContext.Identity(7,"USER"));
        var model=new DeadlineChatModel(blocked,Duration.ofMillis(100));
        var failure=assertTimeout(Duration.ofSeconds(2),()->assertThrows(AiFailure.class,()->model.call(new Prompt("test"))));
        assertEquals(AiFailure.Category.TOTAL_TIMEOUT,failure.category());assertTrue(interrupted.await(1,TimeUnit.SECONDS));
        UserContext.clear();assertNull(AiDeadline.call(Duration.ofSeconds(1),UserContext::currentOrNull));
    }
    @Test void nestedCallsCannotResetAnOuterDeadline() {
        var failure=assertTimeout(Duration.ofSeconds(2),()->assertThrows(AiFailure.class,()->AiDeadline.call(Duration.ofMillis(100),
            ()->AiDeadline.call(Duration.ofSeconds(30),()->{try{Thread.sleep(10000);}catch(InterruptedException e){throw new RuntimeException(e);}return "late";}))));
        assertEquals(AiFailure.Category.TOTAL_TIMEOUT,failure.category());
    }
    @Test void continuousStreamStillHasTotalDeadline()throws Exception {
        var cancelled=new CountDownLatch(1);
        ChatModel model=new ChatModel(){
            public ChatResponse call(Prompt prompt){throw new AssertionError("expected stream");}
            public Flux<ChatResponse> stream(Prompt prompt){return Flux.interval(Duration.ofMillis(10))
                .map(i->new ChatResponse(List.of(new Generation(new AssistantMessage("chunk"))))).doOnCancel(cancelled::countDown);}
        };
        var failure=assertThrows(AiFailure.class,()->new DeadlineChatModel(model,Duration.ofMillis(100)).stream(new Prompt("test")).collectList().block(Duration.ofSeconds(2)));
        assertEquals(AiFailure.Category.TOTAL_TIMEOUT,failure.category());assertTrue(cancelled.await(1,TimeUnit.SECONDS),"总超时必须取消上游订阅");
    }
}
