package org.example.wechat.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.constants.ReceiverTypeConstant;
import org.example.wechat.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private WsSessionManager sessionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatService chatService;

    @Autowired
    @Qualifier("pushExecutor")
    private ExecutorService pushExecutor;

    // ── session 存储（线程安全）──
    private final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // ── 用户级发送锁：防止同一用户并发 sendMessage 竞态 ──
    private final ConcurrentHashMap<Long, ReentrantLock> sendLocks = new ConcurrentHashMap<>();

    // ── 群聊已读游标（生产环境应换成 Redis） ──
    private final ConcurrentHashMap<String, Long> groupLastReadMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> lastHeartbeatMap = new ConcurrentHashMap<>();

    //  连接建立
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 握手拦截器已经校验过 token，userId 直接从 attributes 取
        // 如果握手失败，这个方法根本不会被调用，无需再做 token 校验
        Long userId = (Long) session.getAttributes().get("userId");

        if (userId == null) {
            // 理论上不会走到这里（握手拦截器已保证），防御性处理
            log.warn("afterConnectionEstablished：attributes 中无 userId，关闭连接");
            closeQuietly(session, new CloseStatus(4001, "认证信息缺失"));
            return;
        }

        sendLocks.putIfAbsent(userId, new ReentrantLock());

        // 断线重连：关闭旧 session
        WebSocketSession oldSession = sessions.put(userId, session);
        if (oldSession != null && oldSession.isOpen()
                && !oldSession.getId().equals(session.getId())) {
            log.info("用户 {} 断线重连，关闭旧 session={}", userId, oldSession.getId());
            closeQuietly(oldSession, new CloseStatus(4000, "连接已在其他端建立"));
        }
        log.info("WebSocket 连接成功: userId={}", userId);
        pushOfflineNotification(userId, session);
        lastHeartbeatMap.put(session.getId(), System.currentTimeMillis());
    }

    //  消息处理
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = (Long) session.getAttributes().get("userId");
        String payload = message.getPayload();
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText("");

            switch (type) {
                case "ping":
                    lastHeartbeatMap.put(session.getId(), System.currentTimeMillis());
                    sendRaw(session, "{\"type\":\"pong\"}");
                    break;

                case "read":
                    if (userId == null) break;
                    long targetId = node.path("targetId").asLong(-1);
                    int sessionType = node.path("sessionType").asInt(-1);
                    if (targetId < 0 || sessionType < 0) {
                        log.warn("read 消息参数缺失: {}", payload);
                        break;
                    }
                    if (sessionType == ReceiverTypeConstant.RECTYPE_PUBLIC) {          // 群聊
                        sessionManager.updateGroupLastRead(userId + ":" + targetId, Long.MAX_VALUE);
                    } else if (sessionType == ReceiverTypeConstant.RECTYPE_PRIVATE) {  // 单聊 ← 原来漏掉了
                        chatService.markMessagesAsRead(targetId, sessionType);
                    }
                    break;
                default:
                    log.warn("未知消息类型: {}, userId={}", type, userId);
            }
        } catch (Exception e) {
            log.error("处理 WebSocket 消息异常: userId={}, error={}", userId, e.getMessage());
            sendRaw(session, "{\"type\":\"error\",\"message\":\"消息处理失败\"}");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            // 只移除当前 session，避免断线重连时把新 session 误删
            sessions.remove(userId, session);
            log.info("WebSocket 连接关闭: userId={}, status={}", userId, status);
        }
        UserContext.remove(); // 防止线程复用污染
        lastHeartbeatMap.remove(session.getId());
    }

    //  传输错误（新增）
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = (Long) session.getAttributes().get("userId");
        log.error("WebSocket 传输错误: userId={}, error={}", userId, exception.getMessage());
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    //  主动推送（线程池异步）
    /**
     * 异步推送消息给单个用户
     * 调用方（HTTP 请求线程）立即返回，推送任务交给线程池
     */
    public void sendToUser(Long userId, String message) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            log.debug("用户 {} 不在线，消息已入库等待拉取", userId);
            return;
        }
        // 提交给线程池异步执行
        pushExecutor.execute(() -> doSend(userId, session, message));
    }

    /**
     * 实际发送逻辑：加用户级锁，防止并发 sendMessage 竞态
     * isOpen() 检查 和 sendMessage() 之间如果没有锁，两个线程可能都通过 isOpen() 检查，
     * 然后一个线程关闭了 session，另一个线程还在发，导致异常。
     */
    private void doSend(Long userId, WebSocketSession session, String message) {
        ReentrantLock lock = sendLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        lock.lock();
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
                log.debug("推送成功: userId={}", userId);
            }
        } catch (IOException e) {
            log.error("推送失败: userId={}, error={}", userId, e.getMessage());
            // 发送失败 → 移除僵尸 session，防止内存泄漏
            sessions.remove(userId, session);
        } finally {
            lock.unlock();
        }
    }

    //  群聊已读游标
    public void updateGroupLastRead(String key, Long infoId) {
        groupLastReadMap.put(key, infoId);
    }

    public Long getGroupLastRead(String key) {
        return groupLastReadMap.get(key);
    }

    //  工具方法
    private void pushOfflineNotification(Long userId, WebSocketSession session) {
        try {
            Map<String, Object> notice = new HashMap<>();
            notice.put("type", "offline_notice");
            notice.put("message", "您有离线消息，请刷新会话列表");
            sendRaw(session, objectMapper.writeValueAsString(notice));
        } catch (Exception e) {
            log.error("推送离线通知失败: userId={}", userId, e);
        }
    }

    private void sendRaw(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("sendRaw 失败: sessionId={}", session.getId());
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) session.close(status);
        } catch (IOException e) {
            log.warn("关闭 session 失败: sessionId={}", session.getId());
        }
    }

    /** 应用关闭时优雅停止线程池 */
    @PreDestroy
    public void destroy() {
        pushExecutor.shutdown();
        try {
            if (!pushExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                pushExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            pushExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("WebSocket 推送线程池已关闭");
    }

    //  内部类：线程工厂 & 拒绝策略
    /** 自定义线程名，日志里能直接看出是推送线程 */
    private static class PushThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ws-push-" + counter.incrementAndGet());
            t.setDaemon(true); // 守护线程，应用关闭时不阻塞 JVM 退出
            return t;
        }
    }

    /** 队列满时的拒绝策略：记录警告，不丢弃——消息已入库，客户端重连可拉取 */
    private static class PushRejectedHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("WebSocket 推送队列已满（队列:{}, 活跃线程:{}），本次推送降级为离线消息",
                    executor.getQueue().size(), executor.getActiveCount());
            // 不抛异常，消息已入库，客户端下次连接会收到 offline_notice 后拉取
        }
    }

    @Scheduled(fixedDelay = 30_000)  // 每 30 秒扫描一次
    public void evictDeadSessions() {
        long now = System.currentTimeMillis();
        long timeout = 90_000L; // 90 秒无心跳则踢
        lastHeartbeatMap.forEach((sessionId, lastTime) -> {
            if (now - lastTime > timeout) {
                sessions.forEach((uid, s) -> {
                    if (s.getId().equals(sessionId)) {
                        log.warn("心跳超时，强制断开: userId={}, sessionId={}", uid, sessionId);
                        closeQuietly(s, new CloseStatus(4002, "心跳超时"));
                    }
                });
            }
        });
    }
}