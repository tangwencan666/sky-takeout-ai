package com.tang.sky.ai.security;

import com.tang.sky.common.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;

/** 单进程固定窗口限流：每身份每分钟12次AI生成；部署多实例时应换共享限流。 */
@Component
public class AiRateLimiter {
    private record Window(long minute, int count) {}
    private final Map<UserContext.Identity, Window> windows = new HashMap<>();
    private final Clock clock;
    public AiRateLimiter() { this(Clock.systemUTC()); }
    public AiRateLimiter(Clock clock) { this.clock = clock; }
    public synchronized void acquire(UserContext.Identity identity) {
        long minute = clock.millis() / 60000;
        windows.entrySet().removeIf(e -> e.getValue().minute() != minute);
        var current = windows.get(identity);
        if (current == null && windows.size() >= 10000 || current != null && current.count() >= 12)
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        windows.put(identity, new Window(minute, current == null ? 1 : current.count() + 1));
    }
}
