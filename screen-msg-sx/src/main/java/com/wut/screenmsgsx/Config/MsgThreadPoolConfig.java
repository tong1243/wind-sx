package com.wut.screenmsgsx.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class MsgThreadPoolConfig {
    @Bean("msgTaskAsyncPool")
    public Executor msgTaskAsyncPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程池大小
        executor.setCorePoolSize(64);
        // 最大线程数
        executor.setMaxPoolSize(200);
        // 队列容量
        executor.setQueueCapacity(200);
        // 活跃时间
        executor.setKeepAliveSeconds(300);
        // 线程名字前缀
        executor.setThreadNamePrefix("MESSAGE MODULE EXECUTOR-");

        // 如何处理新任务(由调用者所在的线程来执行)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

}
