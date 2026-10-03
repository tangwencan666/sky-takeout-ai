package com.tang.sky;

import com.tang.sky.common.UserContext;
import com.tang.sky.config.JwtService;
import com.tang.sky.pojo.AuthModels.Credentials;
import com.tang.sky.service.AuthService;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.publisher.Flux;
import static org.junit.jupiter.api.Assertions.*;

/** 真实HTTP和数据库，模型故障为受控替身；绝不调用收费Provider。 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"AI_MODE=mock","AI_STREAM_IDLE_TIMEOUT_MS=100","AI_SSE_TIMEOUT_MS=2000"})
@Import(AiFailureHttpTest.ModelConfiguration.class)
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class AiFailureHttpTest {
    @LocalServerPort int port;
    @Autowired AuthService auth;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired ControlledModel model;
    @Autowired @Qualifier("aiExecutor") ThreadPoolTaskExecutor executor;

    @Test void providerFailureTimeoutAndClientDisconnectAreIsolated()throws Exception{
        String username="ai_failure_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        String testPassword=UUID.randomUUID().toString();
        long id=auth.register(new Credentials(username,testPassword)).id();
        var client=HttpClient.newHttpClient();String token=jwt.issue(id,"USER");
        try{
            model.behavior="error";
            var failed=client.send(post("/user/ai/chat",token),HttpResponse.BodyHandlers.ofString());
            assertEquals(503,failed.statusCode());assertFalse(failed.body().contains("provider-private-detail"));
            var streamError=client.send(post("/user/ai/chat/stream",token),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,streamError.statusCode());assertTrue(streamError.body().contains("event:error"));assertFalse(streamError.body().contains("event:done"));
            assertEquals(200,client.send(get("/health",token),HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(200,client.send(get("/user/cart",token),HttpResponse.BodyHandlers.ofString()).statusCode());

            model.behavior="idle";model.cancelled=new CountDownLatch(1);
            var timedOut=client.send(post("/user/ai/chat/stream",token),HttpResponse.BodyHandlers.ofString());
            assertTrue(timedOut.body().contains("event:error"));assertTrue(model.cancelled.await(2,TimeUnit.SECONDS));

            model.behavior="continuous";model.cancelled=new CountDownLatch(1);
            var open=client.send(post("/user/ai/chat/stream",token),HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200,open.statusCode());
            try(var body=open.body()){assertTrue(body.readNBytes(20).length>0);}
            assertTrue(model.cancelled.await(3,TimeUnit.SECONDS),"浏览器断开后必须取消上游模型订阅");
            for(int i=0;i<8;i++)assertNull(executor.submit(UserContext::currentOrNull).get(2,TimeUnit.SECONDS),"AI工作线程不能残留前一用户身份");
        }finally{jdbc.update("DELETE FROM `user` WHERE id=? AND username=?",id,username);}
    }

    private HttpRequest post(String path,String token){
        return request(path,token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"测试问题\"}")).build();
    }
    private HttpRequest get(String path,String token){return request(path,token).GET().build();}
    private HttpRequest.Builder request(String path,String token){return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+token);}

    @TestConfiguration static class ModelConfiguration {
        @Bean @Primary ControlledModel controlledModel(){return new ControlledModel();}
    }
    static class ControlledModel implements ChatModel {
        volatile String behavior="error";
        volatile CountDownLatch cancelled=new CountDownLatch(1);
        public ChatResponse call(Prompt prompt){throw new IllegalStateException("provider-private-detail");}
        public Flux<ChatResponse> stream(Prompt prompt){
            if("idle".equals(behavior))return Flux.<ChatResponse>never().doOnCancel(cancelled::countDown);
            if("continuous".equals(behavior))return Flux.interval(Duration.ofMillis(15)).map(i->new ChatResponse(List.of(new Generation(new AssistantMessage("piece"))))).doOnCancel(cancelled::countDown);
            return Flux.error(new IllegalStateException("provider-private-detail"));
        }
    }
}
