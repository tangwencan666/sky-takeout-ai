package com.tang.sky.ai.config;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
/** 限制并行模型请求与等待队列，避免无限创建线程拖垮普通外卖请求。 */
@Configuration
public class AiExecutorConfig {
    @Bean(name="aiExecutor")public ThreadPoolTaskExecutor executor(){var pool=new ThreadPoolTaskExecutor();pool.setCorePoolSize(2);pool.setMaxPoolSize(4);pool.setQueueCapacity(16);pool.setThreadNamePrefix("sky-ai-");pool.setWaitForTasksToCompleteOnShutdown(false);pool.initialize();return pool;}
}
