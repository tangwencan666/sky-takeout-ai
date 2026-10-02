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
/** 手动装配模型：没有Key也能运行传统业务；默认Mock绝不发送网络模型请求。 */
@Configuration
public class AiConfig {
    @Bean public ChatModel chatModel(@Value("${AI_MODE:mock}")String mode,
            @Value("${AI_API_KEY:}")String key,@Value("${AI_BASE_URL:https://api.deepseek.com}")String baseUrl,
            @Value("${AI_MODEL:deepseek-chat}")String model){
        if(!"live".equals(mode))return new MockChatModel();
        if(key.isBlank())return prompt->{throw new BusinessException(ErrorCode.AI_UNAVAILABLE);};
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(5000);factory.setReadTimeout(30000);
        var api=OpenAiApi.builder().baseUrl(baseUrl).apiKey(key).restClientBuilder(RestClient.builder().requestFactory(factory)).build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.3).maxTokens(2048).build()).build();
    }
}
