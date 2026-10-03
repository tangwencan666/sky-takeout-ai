package com.tang.sky.ai.config;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import java.util.List;
/** 明确标注的本地Mock支持同步与流式协议测试，不伪装真实模型推理。 */
public class MockChatModel implements ChatModel {
    private static final String TEXT="【Mock演示】模型未启用。请使用菜单浏览真实菜品；此回复不代表真实模型推荐。";
    public ChatResponse call(Prompt prompt){return response(TEXT);}
    public Flux<ChatResponse> stream(Prompt prompt){return Flux.fromIterable(TEXT.codePoints().mapToObj(c->new String(Character.toChars(c))).toList()).delayElements(java.time.Duration.ofMillis(20)).map(MockChatModel::response);}
    private static ChatResponse response(String text){return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));}
}
