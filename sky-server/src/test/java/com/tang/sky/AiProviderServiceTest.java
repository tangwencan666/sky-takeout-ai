package com.tang.sky;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tang.sky.ai.service.AiProviderService;
import com.tang.sky.common.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.tang.sky.ai.security.AiFailure;
import java.util.concurrent.*;
import java.time.Duration;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.openai.OpenAiChatOptions;
import static org.junit.jupiter.api.Assertions.*;

/** 全部ChatModel为内存替身，仅验证应用状态机；不能视为真实Provider成功证据。 */
class AiProviderServiceTest {
    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void successfulFixedProbeIsBoundedWithoutToolsOrMemoryAndCachedPerInstance()throws Exception {
        var calls = new AtomicInteger();
        ChatModel stub = prompt -> {
            calls.incrementAndGet();
            assertEquals(1, prompt.getInstructions().size());
            assertInstanceOf(UserMessage.class, prompt.getInstructions().get(0));
            assertEquals("你好", prompt.getInstructions().get(0).getText());
            var options = (OpenAiChatOptions) prompt.getOptions();
            assertEquals(64, options.getMaxTokens());
            assertFalse(options.getInternalToolExecutionEnabled());
            assertTrue(options.getToolCallbacks() == null || options.getToolCallbacks().isEmpty());
            assertTrue(options.getToolNames() == null || options.getToolNames().isEmpty());
            assertEquals("none", options.getReasoningEffort());
            return new ChatResponse(List.of(new Generation(new AssistantMessage("你好，本地测试替身回复"))));
        };
        String fakeCredential = UUID.randomUUID().toString();
        var provider = new AiProviderService(stub, "live", fakeCredential, "https://api.deepseek.com", "deepseek-flash");
        assertFalse(provider.status().providerVerified());
        assertTrue(provider.status().keyConfigured());
        assertFalse(new ObjectMapper().writeValueAsString(provider.status()).contains(fakeCredential));
        UserContext.set(new UserContext.Identity(1,"USER"));
        var first = provider.probe(); var cached = provider.probe();
        assertEquals(first, cached); assertEquals(1, calls.get());
        assertTrue(first.providerVerified()); assertTrue(provider.status().providerVerified());
        assertNotNull(provider.status().lastVerifiedAt());
        assertEquals(first.instanceId(), provider.status().instanceId());
    }

    @Test void missingKeyAndMockNeverInvokeModelOrClaimProviderSuccess() {
        ChatModel forbidden = prompt -> { throw new AssertionError("不能调用模型"); };
        UserContext.set(new UserContext.Identity(1,"USER"));
        var missing = new AiProviderService(forbidden,"live","","https://api.deepseek.com","deepseek-flash");
        assertEquals(ErrorCode.AI_UNAVAILABLE,assertThrows(BusinessException.class,missing::probe).error());
        assertFalse(missing.status().providerVerified());
        var mock = new AiProviderService(forbidden,"mock",UUID.randomUUID().toString(),"https://api.deepseek.com","deepseek-flash");
        assertFalse(mock.probe().providerVerified()); assertFalse(mock.status().providerVerified());
        assertTrue(mock.probe().message().contains("Mock"));
    }

    @Test void probeRequiresAuthenticatedUserAndFailureCannotManufactureSuccess() {
        var calls = new AtomicInteger();
        ChatModel failed = prompt -> { calls.incrementAndGet(); throw new IllegalStateException("sensitive-provider-error"); };
        var provider = new AiProviderService(failed,"live",UUID.randomUUID().toString(),"https://api.deepseek.com","deepseek-flash");
        assertThrows(BusinessException.class,provider::probe); assertEquals(0,calls.get());
        UserContext.set(new UserContext.Identity(1,"ADMIN"));
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,provider::probe).error());
        UserContext.set(new UserContext.Identity(1,"USER"));
        var error = assertThrows(BusinessException.class,provider::probe);
        assertEquals(ErrorCode.AI_UNAVAILABLE,error.error()); assertFalse(error.getMessage().contains("sensitive-provider-error"));
        assertEquals(1,calls.get()); assertFalse(provider.status().providerVerified());
    }
    @Test void concurrentProbeIsRejectedImmediatelyRatherThanWaitingOnNetworkLock()throws Exception {
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        ChatModel waiting=prompt->{started.countDown();try{release.await();}catch(InterruptedException e){throw new RuntimeException(e);}return new ChatResponse(List.of(new Generation(new AssistantMessage("你好"))));};
        var provider=new AiProviderService(waiting,"live",UUID.randomUUID().toString(),"https://api.deepseek.com","deepseek-flash");
        var executor=Executors.newSingleThreadExecutor();
        try{
            var first=executor.submit(()->{UserContext.set(new UserContext.Identity(1,"USER"));try{return provider.probe();}finally{UserContext.clear();}});
            assertTrue(started.await(2,TimeUnit.SECONDS));UserContext.set(new UserContext.Identity(1,"USER"));
            var failure=assertTimeoutPreemptively(Duration.ofMillis(500),()->{
                UserContext.set(new UserContext.Identity(1,"USER"));
                try{return assertThrows(AiFailure.class,provider::probe);}finally{UserContext.clear();}
            });
            assertEquals(AiFailure.Category.IN_PROGRESS,failure.category());
            release.countDown();assertTrue(first.get(2,TimeUnit.SECONDS).providerVerified());
        }finally{release.countDown();executor.shutdownNow();}
    }
    @Test void probeTotalTimeoutDoesNotCreateSuccessfulEvidence()throws Exception {
        var interrupted=new CountDownLatch(1);
        ChatModel waiting=prompt->{try{Thread.sleep(10000);}catch(InterruptedException e){interrupted.countDown();throw new RuntimeException(e);}return new ChatResponse(List.of(new Generation(new AssistantMessage("late"))));};
        var provider=new AiProviderService(waiting,"live",UUID.randomUUID().toString(),"https://api.deepseek.com","deepseek-flash");
        ReflectionTestUtils.setField(provider,"probeTimeout",Duration.ofMillis(100));
        UserContext.set(new UserContext.Identity(1,"USER"));
        var failure=assertTimeout(Duration.ofSeconds(2),()->assertThrows(AiFailure.class,provider::probe));
        assertEquals(AiFailure.Category.TOTAL_TIMEOUT,failure.category());assertTrue(interrupted.await(1,TimeUnit.SECONDS));
        assertFalse(provider.status().providerVerified());assertNull(provider.status().lastVerifiedAt());
    }
}
