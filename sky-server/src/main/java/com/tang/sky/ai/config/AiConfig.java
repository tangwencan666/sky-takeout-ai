package com.tang.sky.ai.config;
import com.tang.sky.common.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.util.List;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.io.IOException;
import com.tang.sky.ai.security.AiFailure;
import org.springframework.core.io.buffer.DataBufferUtils;
/** 手动装配模型：没有Key也能运行传统业务；默认Mock绝不发送网络模型请求。 */
@Configuration
public class AiConfig {
    @Bean public ChatModel chatModel(@Value("${AI_MODE:mock}")String mode,
            @Value("${AI_API_KEY:}")String key,@Value("${AI_BASE_URL:https://api.deepseek.com}")String baseUrl,
            @Value("${AI_MODEL:deepseek-flash}")String model){
        AiProviderConfiguration.validateMode(mode);
        if("mock".equals(mode))return new MockChatModel();
        var endpoint=AiProviderConfiguration.endpoint(baseUrl);
        var options=AiProviderConfiguration.options(endpoint,model,2048);
        if(key.isBlank())return prompt->{throw AiFailure.of(AiFailure.Category.MISSING_KEY);};
        var httpClient=java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        // JDK HTTP客户端支持线程中断取消；另有整个模型调用35秒截止覆盖正文慢滴和工具多轮。
        var factory=new JdkClientHttpRequestFactory(httpClient);factory.setReadTimeout(Duration.ofSeconds(30));
        var connector=new JdkClientHttpConnector(httpClient);
        connector.setReadTimeout(Duration.ofSeconds(30));
        var webClient=WebClient.builder().clientConnector(connector).filter((request,next)->next.exchange(request).flatMap(response->{
            if(response.statusCode().isError()){
                int status=response.statusCode().value();
                return DataBufferUtils.join(DataBufferUtils.takeUntilByteCount(response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class),4096))
                    .map(buffer->{try{byte[] bytes=new byte[buffer.readableByteCount()];buffer.read(bytes);return bytes;}finally{DataBufferUtils.release(buffer);}})
                    .defaultIfEmpty(new byte[0]).timeout(Duration.ofSeconds(1)).onErrorReturn(new byte[0])
                    .flatMap(bytes->Mono.error(AiFailure.http(status,bytes)));
            }
            return Mono.just(response);
        }));
        // Provider的错误正文可能回显请求，统一丢弃，避免第三方默认错误处理器记录敏感内容。
        ResponseErrorHandler errors=new ResponseErrorHandler(){
            public boolean hasError(ClientHttpResponse response)throws IOException{return response.getStatusCode().isError();}
            public void handleError(java.net.URI url,org.springframework.http.HttpMethod method,ClientHttpResponse response)throws IOException{
                int status=response.getStatusCode().value();byte[] bytes=null;
                if(status==400||status==404||status==429)bytes=response.getBody().readNBytes(4096);
                throw AiFailure.http(status,bytes);
            }
        };
        var api=OpenAiApi.builder().baseUrl(endpoint.baseUrl()).completionsPath(endpoint.completionsPath()).apiKey(key).restClientBuilder(RestClient.builder().requestFactory(factory))
            .webClientBuilder(webClient).responseErrorHandler(errors).build();
        // 教学环境不自动重试付费请求；工具失败直接终止，避免模型反复重试失败工具。
        var manager=DefaultToolCallingManager.builder()
            .toolExecutionExceptionProcessor(new DefaultToolExecutionExceptionProcessor(true)).build();
        var realModel=OpenAiChatModel.builder().openAiApi(api).retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
            .toolCallingManager(manager)
            .defaultOptions(options).build();
        return new DeadlineChatModel(realModel,Duration.ofSeconds(35));
    }
}
