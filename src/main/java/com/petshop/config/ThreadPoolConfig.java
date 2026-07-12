package com.petshop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {

    @Bean("homeAssemblyThreadPool")
    public Executor homeAssemblyThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：避免频繁创建销毁，由于涉及数据库IO，设大一些
        executor.setCorePoolSize(10);
        // 最大线程数：扛住瞬时高并发首页请求
        executor.setMaxPoolSize(50);
        // 队列容量：缓冲突发请求
        executor.setQueueCapacity(100);
        // 活跃时间
        executor.setKeepAliveSeconds(60);
        // 线程名称前缀，方便排查日志
        executor.setThreadNamePrefix("HomeAssembly-");
        // 拒绝策略：如果满了，由调用者所在的主线程（Tomcat NIO）自己去执行，防止丢数据
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
