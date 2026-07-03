package org.example.wechat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("pushExecutor")
    public ExecutorService pushExecutor() {
        return new ThreadPoolExecutor(
                4,
                16,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadFactory() {
                    private final java.util.concurrent.atomic.AtomicInteger counter =
                            new java.util.concurrent.atomic.AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "ws-push-" + counter.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                (r, executor) -> log.warn(
                        "WebSocket 推送队列已满，queue={}, active={}",
                        executor.getQueue().size(), executor.getActiveCount())
        );
    }
}
