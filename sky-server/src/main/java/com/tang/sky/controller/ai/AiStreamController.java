package com.tang.sky.controller.ai;
import com.tang.sky.ai.model.ChatModels.*;
import com.tang.sky.ai.service.AiAssistantService;
import com.tang.sky.common.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** SSE错误以error事件发送；断开、超时都取消模型任务，工作线程finally清理用户身份。 */
@RestController
public class AiStreamController {
    private final AiAssistantService service;private final ThreadPoolTaskExecutor executor;
    public AiStreamController(AiAssistantService service,@Qualifier("aiExecutor")ThreadPoolTaskExecutor executor){this.service=service;this.executor=executor;}
    @PostMapping(value="/user/ai/chat/stream",produces="text/event-stream")
    public SseEmitter stream(@Valid @RequestBody Request input){
        var identity=UserContext.require();var emitter=new SseEmitter(45000L);var cancelled=new AtomicBoolean();var task=new AtomicReference<Future<?>>();
        Runnable cancel=()->{cancelled.set(true);var future=task.get();if(future!=null)future.cancel(true);};
        emitter.onCompletion(cancel);emitter.onTimeout(()->{cancel.run();emitter.complete();});emitter.onError(e->cancel.run());
        try{task.set(executor.submit(()->{
            try{UserContext.set(identity);var result=service.streamChat(input,chunk->send(emitter,"delta",Map.of("text",chunk)),cancelled::get);send(emitter,"done",Map.of("conversationId",result.conversationId(),"mode",result.mode()));emitter.complete();}
            catch(Exception e){if(!cancelled.get()){try{send(emitter,"error",Map.of("message","AI生成中断，请稍后重试"));}catch(Exception ignored){/* 连接已关闭，无可发送的响应。 */}emitter.complete();}}
            finally{UserContext.clear();}
        }));if(cancelled.get())task.get().cancel(true);}
        catch(org.springframework.core.task.TaskRejectedException e){throw new BusinessException(ErrorCode.AI_UNAVAILABLE);}
        return emitter;
    }
    private static void send(SseEmitter emitter,String event,Object data){try{emitter.send(SseEmitter.event().name(event).data(data));}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
}
