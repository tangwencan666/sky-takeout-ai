package com.tang.sky.ai.security;

import com.tang.sky.common.BusinessException;
import com.tang.sky.common.ErrorCode;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/** 只记录关联元数据；不记录提示词、工具参数/结果、JWT或凭据。 */
public final class AiRequestAudit {
    private static final Logger log = LoggerFactory.getLogger(AiRequestAudit.class);
    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();
    public static final int MAX_TOOL_CALLS = 8;

    private AiRequestAudit() {}

    public static <T> T run(String feature, String conversationId, String model, Supplier<T> action) {
        Session previous = CURRENT.get();
        Session session = new Session(feature, safe(conversationId), safe(model));
        CURRENT.set(session);
        boolean success = false;
        String category = "-";Integer upstreamStatus = null;
        log.info("AI start requestId={} conversationId={} feature={} model={}",session.id,session.conversation,session.feature,session.model);
        try {
            T result = action.get();
            success = true;
            return result;
        } catch(RuntimeException e) {
            if(e instanceof com.tang.sky.ai.model.MealPlanRejectedException rejection)category="MEAL_PLAN_"+rejection.reason().name();
            else if(e instanceof BusinessException business && !(e instanceof AiFailure))category="BUSINESS_"+business.error().name();
            else {var failure=AiFailure.classify(e);category=failure.category().name();upstreamStatus=failure.upstreamStatus();}
            throw e;
        } finally {
            log.info("AI requestId={} conversationId={} feature={} model={} durationMs={} toolCall={} toolCount={} success={} category={} upstreamStatus={}",
                session.id, session.conversation, session.feature, session.model, session.elapsed(),
                session.calls.get() > 0, session.calls.get(), success,category,upstreamStatus);
            // 工作线程会复用，必须恢复而不是把下一次请求关联到上一次。
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static ToolCallback[] callbacks(Object... tools) {
        Session session = CURRENT.get();
        if (session == null) throw new IllegalStateException("AI audit scope is required");
        return Arrays.stream(ToolCallbacks.from(tools)).map(callback -> wrap(callback, session)).toArray(ToolCallback[]::new);
    }
    public static void mealPlanValidation(String category,int dishCount,java.math.BigDecimal budget,java.math.BigDecimal total){
        Session session=CURRENT.get();
        log.info("AI meal-plan validation requestId={} category={} dishCount={} budget={} recalculatedTotal={}",
            session==null?"-":session.id,safe(category),dishCount,budget,total);
    }

    static boolean hasScope() { return CURRENT.get() != null; }

    private static ToolCallback wrap(ToolCallback delegate, Session session) {
        return new ToolCallback() {
            public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }
            public ToolMetadata getToolMetadata() { return delegate.getToolMetadata(); }
            public String call(String input) { return call(input, null); }
            public String call(String input, ToolContext context) {
                long started = System.nanoTime();
                boolean success = false;
                try {
                    // 回调闭包携带请求预算，模型异步换线程也不能绕开上限。
                    if (session.calls.incrementAndGet() > MAX_TOOL_CALLS || session.elapsed() > 45_000)
                        throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
                    String result = delegate.call(input, context);
                    success = true;
                    return result;
                } finally {
                    log.info("AI tool requestId={} conversationId={} model={} tool={} durationMs={} success={}",
                        session.id, session.conversation, session.model, getToolDefinition().name(),
                        (System.nanoTime() - started) / 1_000_000, success);
                }
            }
        };
    }

    private static String safe(String value) {
        if (value == null) return "-";
        String cleaned = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return cleaned.substring(0, Math.min(cleaned.length(), 100));
    }

    private static final class Session {
        final String id = UUID.randomUUID().toString();
        final String feature, conversation, model;
        final long started = System.nanoTime();
        final AtomicInteger calls = new AtomicInteger();
        Session(String feature, String conversation, String model) {
            this.feature = feature; this.conversation = conversation; this.model = model;
        }
        long elapsed() { return (System.nanoTime() - started) / 1_000_000; }
    }
}
