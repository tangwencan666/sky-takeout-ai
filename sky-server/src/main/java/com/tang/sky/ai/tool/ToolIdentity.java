package com.tang.sky.ai.tool;
import com.tang.sky.common.*;
import org.springframework.ai.chat.model.ToolContext;
import java.util.function.Supplier;
/** ToolContext由后端构造，不进入模型schema；异步线程内临时恢复身份，finally恢复原上下文。 */
public final class ToolIdentity {
    private ToolIdentity(){}
    public static <T>T run(ToolContext context,Supplier<T> action){
        var previous=UserContext.currentOrNull();Object candidate=context==null?previous:context.getContext().get("identity");
        if(!(candidate instanceof UserContext.Identity identity)||!identity.role().equals("USER"))throw new BusinessException(ErrorCode.UNAUTHORIZED);
        try{UserContext.set(identity);return action.get();}finally{if(previous==null)UserContext.clear();else UserContext.set(previous);}
    }
}
