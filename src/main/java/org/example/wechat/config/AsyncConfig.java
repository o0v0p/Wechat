package org.example.wechat.config;

import com.alibaba.ttl.threadpool.TtlExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;

/**
 * 异步线程池配置
 *
 * 核心：用 TtlExecutors.getTtlExecutorService() 包装原始线程池。
 * 包装后，每次 execute()/submit() 提交任务时，TTL 自动在当前线程
 * 捕获所有 TransmittableThreadLocal 的值，在线程池线程执行任务前
 * 还原这些值，执行完后清理——无论任务是否抛异常。
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * WebSocket 推送专用线程池（TTL 包装）
     * 注入到 ChatWebSocketHandler，替换掉 Handler 内部自建的 ThreadPoolExecutor
     *
     * 参数：
     *   core=4, max=16, queue=1000
     *   拒绝策略：记日志，不抛异常（消息已入库，客户端重连可拉取）
     */
    @Bean("pushExecutor")
    public ExecutorService pushExecutor() {
        ThreadPoolExecutor raw = new ThreadPoolExecutor(
                4,
                16,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadFactory() {
                    private final java.util.concurrent.atomic.AtomicInteger counter
                            = new java.util.concurrent.atomic.AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "ws-push-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                },
                (r, executor) -> log.warn(
                        "WebSocket 推送队列已满(queue={}, active={})，消息降级为离线",
                        executor.getQueue().size(), executor.getActiveCount())
        );
        // 关键：TTL 包装，提交任务时自动捕获/还原/清理 TransmittableThreadLocal
        return TtlExecutors.getTtlExecutorService(raw);
    }
}