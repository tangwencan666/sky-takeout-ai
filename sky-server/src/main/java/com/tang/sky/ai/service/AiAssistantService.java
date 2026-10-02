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
    @org.springframework.beans.factory.annotation.Autowired
    public void registerDishTools(com.tang.sky.ai.tool.DishTools dishTools){tools.add(dishTools);}
    public AiAssistantService(ChatModel model,PromptCatalog prompts,@Value("${AI_MODE:mock}")String mode){this.client=ChatClient.create(model);this.prompts=prompts;this.mode=mode;}
    public String mode(){return mode;}
    public com.tang.sky.ai.model.ChatModels.Reply chat(com.tang.sky.ai.model.ChatModels.Request input){
        long user=UserContext.userId();
        String conversation=input.conversationId()==null?java.util.UUID.randomUUID().toString():input.conversationId();
        log.info("AI chat userId={} mode={}",user,mode);
        return new com.tang.sky.ai.model.ChatModels.Reply(conversation,reply(input.message()),mode);
    }
    public String reply(String message){
        try{var request=client.prompt().system(prompts.ordering()).user(message);if(!tools.isEmpty())request.tools(tools.toArray());return request.call().content();}
        catch(RuntimeException e){log.warn("AI request failed type={}",e.getClass().getSimpleName());throw new BusinessException(ErrorCode.AI_UNAVAILABLE);}
    }
}
