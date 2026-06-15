package org.example.wechat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;

/**
 * 异步线程池配置
 *
 * 说明：本项目 UserContext 使用普通 ThreadLocal，仅在 HTTP 请求线程内有效。
 * WebSocket 推送任务通过线程池执行时，userId 已作为参数显式传入，无需跨线程传播。
 * 因此不需要 TTL（TransmittableThreadLocal）包装，移除 transmittable-thread-local 依赖即可。
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * WebSocket 推送专用线程池
     * 注入到 WsSessionManager，用于异步推送消息给在线用户。
     *
     * 参数：
     *   core=4, max=16, queue=1000
     *   拒绝策略：记日志，不抛异常（消息已入库，客户端重连可拉取）
     */
    @Bean("pushExecutor")
    public ExecutorService pushExecutor() {
        return new ThreadPoolExecutor(
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
    }
}
