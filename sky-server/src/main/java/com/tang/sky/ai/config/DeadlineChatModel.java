package com.tang.sky.ai.config;

import com.tang.sky.ai.security.*;
import java.time.Duration;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.*;
import reactor.core.publisher.*;

/** 保留Spring AI实现，仅增加总截止；总时间包含内部Tool Calling往返，不能被慢滴响应重置。 */
public final class DeadlineChatModel implements ChatModel {
    private final ChatModel delegate;
    private final Duration timeout;
    public DeadlineChatModel(ChatModel delegate, Duration timeout) { this.delegate = delegate; this.timeout = timeout; }
    public ChatOptions getDefaultOptions() { return delegate.getDefaultOptions(); }
    public ChatResponse call(Prompt prompt) { return AiDeadline.call(timeout, () -> delegate.call(prompt)); }
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> {
            var expired = new java.util.concurrent.atomic.AtomicBoolean();
            // takeUntilOther的正常信号会先取消主流；不能让定时器直接onError，
            // 否则当前Reactor版本只终止下游而不取消持续产生数据的主订阅。
            return delegate.stream(prompt)
                .takeUntilOther(Mono.delay(timeout).doOnNext(tick -> expired.set(true)))
                .concatWith(Flux.defer(() -> expired.get()
                    ? Flux.error(AiFailure.of(AiFailure.Category.TOTAL_TIMEOUT)) : Flux.empty()));
        })
            .onErrorMap(AiFailure::classify);
    }
}
