package com.tang.sky.ai.service;
import com.tang.sky.ai.prompt.PromptCatalog;
import com.tang.sky.common.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.*;
/** AI边界隔离模型失败，传统业务不依赖模型成功；不记录用户原文、Key或Token。 */
@Service
public class AiAssistantService {
    private static final Logger log=LoggerFactory.getLogger(AiAssistantService.class);
    protected final ChatClient client;protected final PromptCatalog prompts;private final String mode;
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
        log.info("AI chat userId={} mode={}",user,mode);
        String answer=memory.exchange(user,conversation,input.message(),this::replyMessages);
        return new com.tang.sky.ai.model.ChatModels.Reply(conversation,answer,mode);
    }
    public String reply(String message){
        return replyMessages(java.util.List.of(new org.springframework.ai.chat.messages.UserMessage(message)));
    }
    protected String replyMessages(java.util.List<org.springframework.ai.chat.messages.Message> messages){
        try{var request=client.prompt().system(prompts.ordering()).messages(messages);if(!tools.isEmpty())request.tools(tools.toArray());return request.call().content();}
        catch(RuntimeException e){log.warn("AI request failed type={}",e.getClass().getSimpleName());throw new BusinessException(ErrorCode.AI_UNAVAILABLE);}
    }
}
