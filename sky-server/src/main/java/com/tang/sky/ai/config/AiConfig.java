package com.tang.sky.ai.config;
import com.tang.sky.common.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
        if(key.isBlank())return prompt->{throw new BusinessException(ErrorCode.AI_UNAVAILABLE);};
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(5000);factory.setReadTimeout(30000);
        var connector=new JdkClientHttpConnector(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        connector.setReadTimeout(Duration.ofSeconds(30));
        var webClient=WebClient.builder().clientConnector(connector).filter((request,next)->next.exchange(request).flatMap(response->{
            if(response.statusCode().isError())return response.releaseBody().then(Mono.error(new BusinessException(ErrorCode.AI_UNAVAILABLE)));
            return Mono.just(response);
        }));
        // Provider的错误正文可能回显请求，统一丢弃，避免第三方默认错误处理器记录敏感内容。
        ResponseErrorHandler errors=new ResponseErrorHandler(){
            public boolean hasError(ClientHttpResponse response)throws IOException{return response.getStatusCode().isError();}
            public void handleError(java.net.URI url,org.springframework.http.HttpMethod method,ClientHttpResponse response){throw new BusinessException(ErrorCode.AI_UNAVAILABLE);}
        };
        var api=OpenAiApi.builder().baseUrl(endpoint.baseUrl()).completionsPath(endpoint.completionsPath()).apiKey(key).restClientBuilder(RestClient.builder().requestFactory(factory))
            .webClientBuilder(webClient).responseErrorHandler(errors).build();
        // 教学环境不自动重试付费请求；工具失败直接终止，避免模型反复重试失败工具。
        var manager=DefaultToolCallingManager.builder()
            .toolExecutionExceptionProcessor(new DefaultToolExecutionExceptionProcessor(true)).build();
        return OpenAiChatModel.builder().openAiApi(api).retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
            .toolCallingManager(manager)
            .defaultOptions(options).build();
    }
}
