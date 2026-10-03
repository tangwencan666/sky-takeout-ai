package com.tang.sky.ai.security;

import com.tang.sky.common.UserContext;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** 独立有界线程池隔离模型阻塞；无等待队列，超时取消并清理身份，不占满普通业务线程。 */
public final class AiDeadline {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final ExecutorService WORKERS = new ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS,
        new SynchronousQueue<>(), task -> {
            var thread = new Thread(task, "sky-provider-" + IDS.incrementAndGet()); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    private AiDeadline() {}

    public static <T> T call(Duration limit, Supplier<T> action) {
        Long inherited = DEADLINE.get();
        if (inherited != null) {
            if (System.nanoTime() >= inherited || Thread.currentThread().isInterrupted()) throw AiFailure.of(AiFailure.Category.TOTAL_TIMEOUT);
            return action.get(); // 探针已建立总截止，内部模型调用不再创建嵌套线程。
        }
        long expires = System.nanoTime() + limit.toNanos();
        var identity = UserContext.currentOrNull();
        Future<T> future;
        try {
            future = WORKERS.submit(() -> {
                DEADLINE.set(expires); if (identity != null) UserContext.set(identity);
                try { return action.get(); }
                finally { DEADLINE.remove(); UserContext.clear(); }
            });
        } catch (RejectedExecutionException e) { throw AiFailure.of(AiFailure.Category.LOCAL_BUSY); }
        try {
            T result = future.get(Math.max(1, expires - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (System.nanoTime() >= expires) throw AiFailure.of(AiFailure.Category.TOTAL_TIMEOUT);
            return result;
        } catch (TimeoutException e) {
            future.cancel(true); throw AiFailure.of(AiFailure.Category.TOTAL_TIMEOUT);
        } catch (InterruptedException e) {
            future.cancel(true); Thread.currentThread().interrupt(); throw AiFailure.of(AiFailure.Category.CANCELLED);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error failure) throw failure;
            throw AiFailure.classify(e.getCause());
        }
    }
}
