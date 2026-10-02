package com.tang.sky.ai.memory;
import com.tang.sky.common.*;
import org.springframework.ai.chat.messages.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
/** 本地窗口记忆：身份来自后端，ID相同但用户不同也是不同会话；网络调用不持有全局锁。 */
public class ConversationMemory {
    private record Key(long user,String conversation){}
    private static final class Entry { List<Message> messages=List.of();Instant touched=Instant.now();boolean busy; }
    private final Map<Key,Entry> entries=new HashMap<>();
    public String exchange(long user,String conversation,String text,Function<List<Message>,String> answer){
        Key key=new Key(user,conversation);Entry entry;
        synchronized(entries){
            Instant cutoff=Instant.now().minus(Duration.ofMinutes(30));entries.entrySet().removeIf(e->!e.getValue().busy&&e.getValue().touched.isBefore(cutoff));
            if(!entries.containsKey(key)&&entries.size()>=1000)throw new BusinessException(ErrorCode.CONFLICT,"会话数量已达上限，请稍后重试");
            entry=entries.computeIfAbsent(key,k->new Entry());
            if(entry.busy)throw new BusinessException(ErrorCode.CONFLICT,"同一会话仍在生成回复");entry.busy=true;
        }
        try{
            var messages=new ArrayList<>(entry.messages);messages.add(new UserMessage(text));String result=answer.apply(List.copyOf(messages));
            if(result==null||result.isBlank())throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
            messages.add(new AssistantMessage(result));entry.messages=List.copyOf(messages.subList(Math.max(0,messages.size()-20),messages.size()));return result;
        }finally{synchronized(entries){entry.busy=false;entry.touched=Instant.now();}}
    }
}
