package com.tang.sky.ai.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tang.sky.common.BusinessException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import static org.junit.jupiter.api.Assertions.*;

/** 验证元数据可追踪、失败清理和工具次数边界，不把Mock当成模型安全能力证明。 */
class AiAuditTest {
    public static class ReadTool {
        final AtomicInteger calls=new AtomicInteger();
        @Tool(description="测试只读回调") public String read(String query){calls.incrementAndGet();return "sensitive-result";}
    }

    @Test void logsMetadataWithoutToolInputOrResultAndCleansFailureScope() {
        Logger logger=(Logger)LoggerFactory.getLogger(AiRequestAudit.class);
        var appender=new ListAppender<ILoggingEvent>();appender.start();logger.addAppender(appender);
        var tool=new ReadTool();
        try{
            assertThrows(BusinessException.class,()->AiRequestAudit.run("test","conversation-7","mock",()->{
                var callback=AiRequestAudit.callbacks(tool)[0];
                for(int i=0;i<AiRequestAudit.MAX_TOOL_CALLS;i++)callback.call("{\"query\":\"private-prompt-marker\"}");
                callback.call("{\"query\":\"private-prompt-marker\"}");return null;
            }));
            assertEquals(AiRequestAudit.MAX_TOOL_CALLS,tool.calls.get());
            assertFalse(AiRequestAudit.hasScope());
            var logs=appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertTrue(logs.stream().anyMatch(s->s.contains("conversationId=conversation-7")&&s.contains("model=mock")&&s.contains("durationMs=")&&s.contains("success=false")));
            assertTrue(logs.stream().anyMatch(s->s.contains("tool=read")));
            assertTrue(logs.stream().allMatch(s->s.contains("requestId=")));
            assertTrue(logs.stream().noneMatch(s->s.contains("private-prompt-marker")||s.contains("sensitive-result")));
            AiRequestAudit.run("next",null,"mock",()->{assertTrue(AiRequestAudit.hasScope());return null;});
            assertFalse(AiRequestAudit.hasScope());
        }finally{logger.detachAppender(appender);appender.stop();}
    }
}
