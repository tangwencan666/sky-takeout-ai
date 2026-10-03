package com.tang.sky.ai.service;
import com.tang.sky.ai.prompt.PromptCatalog;
import com.tang.sky.common.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.*;
import com.tang.sky.ai.security.AiRequestAudit;
import com.tang.sky.ai.security.AiFailure;
/** AI边界隔离模型失败，传统业务不依赖模型成功；不记录用户原文、Key或Token。 */
@Service
public class AiAssistantService {
    private static final Logger log=LoggerFactory.getLogger(AiAssistantService.class);
    protected final ChatClient client;protected final PromptCatalog prompts;private final String mode;
    @Value("${AI_MODEL:deepseek-flash}") private String modelName="deepseek-flash";
    @Value("${AI_STREAM_IDLE_TIMEOUT_MS:40000}") private long streamIdleTimeoutMs=40000;
    protected final java.util.List<Object> tools=new java.util.ArrayList<>();
    protected final com.tang.sky.ai.memory.ConversationMemory memory=new com.tang.sky.ai.memory.ConversationMemory();
    @org.springframework.beans.factory.annotation.Autowired
    public void registerDishTools(com.tang.sky.ai.tool.DishTools dishTools){tools.add(dishTools);}
    @org.springframework.beans.factory.annotation.Autowired
    public void registerPrivateTools(com.tang.sky.ai.tool.OrderTools orders,com.tang.sky.ai.tool.CartTools cart){tools.add(orders);tools.add(cart);}
    public AiAssistantService(ChatModel model,PromptCatalog prompts,@Value("${AI_MODE:mock}")String mode){this.client=ChatClient.create(model);this.prompts=prompts;this.mode=mode;}
    public String mode(){return mode;}
    public com.tang.sky.ai.model.ChatModels.Reply chat(com.tang.sky.ai.model.ChatModels.Request input){
        long user=UserContext.userId();
        String conversation=input.conversationId()==null?java.util.UUID.randomUUID().toString():input.conversationId();
        String answer=AiRequestAudit.run("chat",conversation,auditModel(),()->memory.exchange(user,conversation,input.message(),this::replyMessages));
        return new com.tang.sky.ai.model.ChatModels.Reply(conversation,answer,mode);
    }
    public String reply(String message){
        return AiRequestAudit.run("reply",null,auditModel(),()->replyMessages(java.util.List.of(new org.springframework.ai.chat.messages.UserMessage(message))));
    }
    protected String replyMessages(java.util.List<org.springframework.ai.chat.messages.Message> messages){
        try{return com.tang.sky.ai.security.AiOutput.checked(request(messages).call().content());}
        catch(RuntimeException e){throw AiFailure.classify(e);}
    }
    protected ChatClient.ChatClientRequestSpec request(java.util.List<org.springframework.ai.chat.messages.Message> messages){
        var request=client.prompt().system(prompts.ordering()).messages(messages);
        if(!tools.isEmpty())request.toolCallbacks(AiRequestAudit.callbacks(tools.toArray()));
        var identity=UserContext.currentOrNull();if(identity!=null)request.toolContext(java.util.Map.of("identity",identity));return request;
    }
    public com.tang.sky.ai.model.ChatModels.Reply streamChat(com.tang.sky.ai.model.ChatModels.Request input,java.util.function.Consumer<String> delta,java.util.function.BooleanSupplier cancelled){
        long user=UserContext.userId();String conversation=input.conversationId()==null?java.util.UUID.randomUUID().toString():input.conversationId();
        String answer=AiRequestAudit.run("stream",conversation,auditModel(),()->memory.exchange(user,conversation,input.message(),messages->{
            var text=new StringBuilder();
            // 从模型真实流读取；Stream关闭会取消上游订阅，取消/失败时不把半截回复写入Memory。
            try(var stream=request(messages).stream().content().timeout(java.time.Duration.ofMillis(streamIdleTimeoutMs)).toStream()){
                var iterator=stream.iterator();while(true){
                    // hasNext本身可能阻塞等待下一帧，取消信号必须在等待之前检查。
                    if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                    if(!iterator.hasNext())break;
                    if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                    String chunk=iterator.next();text.append(chunk);if(text.length()>16000)throw new BusinessException(ErrorCode.AI_UNAVAILABLE);delta.accept(chunk);
                }
            }
            if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException();return text.toString();
        }));
        return new com.tang.sky.ai.model.ChatModels.Reply(conversation,answer,mode);
    }
    private String auditModel(){return "live".equals(mode)?modelName:"mock";}
}
