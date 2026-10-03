package com.tang.sky;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tang.sky.ai.config.AiConfig;
import com.tang.sky.ai.model.ChatModels.Request;
import com.tang.sky.ai.prompt.PromptCatalog;
import com.tang.sky.ai.service.AiAssistantService;
import com.tang.sky.ai.service.AiProviderService;
import com.tang.sky.common.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 真正执行OpenAI-compatible HTTP适配器，但远端是本机测试服务器，不是付费AI。 */
class LocalProviderTest {
    @Test void compatibleWireProtocolSupportsHelloStreamAndSingleAttemptFailure()throws Exception{
        var calls=new AtomicInteger();var fail=new AtomicBoolean();var requestBody=new AtomicReference<String>();
        var requestPath=new AtomicReference<String>();var authorizationMatches=new AtomicBoolean();
        String localOnlyCredential=UUID.randomUUID().toString();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            calls.incrementAndGet();requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            authorizationMatches.set(("Bearer "+localOnlyCredential).equals(exchange.getRequestHeaders().getFirst("Authorization")));
            boolean streaming=requestBody.get().contains("\"stream\":true");
            String body;
            if(fail.get())body="{\"error\":{\"message\":\"provider-private-detail\"}}";
            else if(streaming)body="data: {\"id\":\"local\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"你\"}}]}\n\n"
                +"data: {\"id\":\"local\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"好\"}}]}\n\n"
                +"data: {\"id\":\"local\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                +"data: [DONE]\n\n";
            else body="{\"id\":\"local\",\"object\":\"chat.completion\",\"model\":\"test-model\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"你好\"},\"finish_reason\":\"stop\"}]}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type",streaming&&!fail.get()?"text/event-stream;charset=UTF-8":"application/json");
            exchange.sendResponseHeaders(fail.get()?500:200,bytes.length);
            try(var output=exchange.getResponseBody()){output.write(bytes);}
        });
        server.start();
        try{
            var model=new AiConfig().chatModel("live",localOnlyCredential,"http://127.0.0.1:"+server.getAddress().getPort(),"test-model");
            var service=new AiAssistantService(model,new PromptCatalog(),"live");
            assertEquals("你好",service.reply("你好"));assertTrue(authorizationMatches.get());
            var json=new ObjectMapper().readTree(requestBody.get());
            assertEquals("test-model",json.get("model").asText());assertEquals(2048,json.get("max_tokens").asInt());
            assertEquals("/v1/chat/completions",requestPath.get());
            UserContext.set(new UserContext.Identity(1,"USER"));
            // 显式/v1 base必须只拼一次；探针只发送固定“你好”且不注册工具。
            String v1Base="http://127.0.0.1:"+server.getAddress().getPort()+"/v1";
            var probeModel=new AiConfig().chatModel("live",localOnlyCredential,v1Base,"test-model");
            var provider=new AiProviderService(probeModel,"live",localOnlyCredential,v1Base,"test-model");
            int beforeProbe=calls.get();
            var probe=provider.probe();provider.probe();
            assertEquals(1,calls.get()-beforeProbe);assertEquals("你好",probe.message());
            assertFalse(probe.providerVerified(),"本机协议替身不能显示为真实Provider已验证");
            assertEquals("local-test",provider.status().provider());
            assertEquals("/v1/chat/completions",requestPath.get());
            var probeJson=new ObjectMapper().readTree(requestBody.get());
            assertEquals(64,probeJson.get("max_tokens").asInt());
            assertEquals(1,probeJson.get("messages").size());assertEquals("你好",probeJson.get("messages").get(0).get("content").asText());
            assertTrue(probeJson.get("tools")==null||probeJson.get("tools").isEmpty());
            var chunks=new ArrayList<String>();
            assertEquals("你好",service.streamChat(new Request("你好","wire"),chunks::add,()->false).message());
            assertTrue(chunks.size()>=2);assertEquals("你好",String.join("",chunks));
            fail.set(true);int before=calls.get();
            assertEquals(ErrorCode.AI_UNAVAILABLE,assertThrows(BusinessException.class,()->service.reply("你好")).error());
            assertEquals(1,calls.get()-before,"失败Provider请求不得自动重试");
        }finally{UserContext.clear();server.stop(0);}
    }
}
