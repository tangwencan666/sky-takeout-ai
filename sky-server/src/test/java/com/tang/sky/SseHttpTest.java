package com.tang.sky;
import com.tang.sky.config.JwtService;
import com.tang.sky.pojo.AuthModels.Credentials;
import com.tang.sky.service.AuthService;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
/** 启动真实HTTP端口读取SSE，仅用随机测试用户，finally定向清理自己创建的记录。 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="AI_MODE=mock")
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class SseHttpTest {
    @LocalServerPort int port;@Autowired AuthService auth;@Autowired JwtService jwt;@Autowired JdbcTemplate jdbc;
    @Test void authenticatedHttpStreamHasDeltaAndDone()throws Exception{
        String name="sse_"+UUID.randomUUID().toString().replace("-", "").substring(0,20);long id=auth.register(new Credentials(name,"SseTest!29")).id();
        try{
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/user/ai/chat/stream")).timeout(Duration.ofSeconds(15))
                .header("Content-Type","application/json").header("Authorization","Bearer "+jwt.issue(id,"USER"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"午餐\",\"conversationId\":\"http\"}")).build();
            var result=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());assertEquals(200,result.statusCode());
            assertTrue(result.body().contains("event:delta"));assertTrue(result.body().contains("event:done"));assertTrue(result.body().contains("\"mode\":\"mock\""));
        }finally{jdbc.update("DELETE FROM `user` WHERE id=? AND username=?",id,name);}
    }
}
