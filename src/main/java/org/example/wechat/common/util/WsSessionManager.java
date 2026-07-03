package org.example.wechat.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class WsSessionManager {

    @Autowired
    @Qualifier("pushExecutor")
    private ExecutorService pushExecutor;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ── userId → session（唯一来源，WebSocketHandler 和 ChatServiceImpl 均通过此处访问）──
    private final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // ── 用户级发送锁：防止 isOpen() 与 sendMessage() 之间的竞态 ──
    private final ConcurrentHashMap<Long, ReentrantLock> sendLocks = new ConcurrentHashMap<>();

    // ── sessionId → 最后心跳时间（毫秒） ──
    private final ConcurrentHashMap<String, Long> lastHeartbeatMap = new ConcurrentHashMap<>();

    private static final String GROUP_READ_KEY_PREFIX = "group:lastRead:";

    // ══════════════════ session 注册/注销 ══════════════════

    /**
     * 注册新 session，返回被替换的旧 session（断线重连时由 Handler 负责关闭旧连接）
     */
    public WebSocketSession register(Long userId, WebSocketSession session) {
        sendLocks.putIfAbsent(userId, new ReentrantLock());
        lastHeartbeatMap.put(session.getId(), System.currentTimeMillis());
        return sessions.put(userId, session);
    }

    /**
     * 注销 session（只移除当前 session，避免重连时误删新 session）
     */
    public void unregister(Long userId, WebSocketSession session) {
        sessions.remove(userId, session);
        lastHeartbeatMap.remove(session.getId());
        // sendLocks 不移除：锁对象可复用，下次重连无需重建
    }

    /**
     * 判断用户是否在线
     */
    public boolean isOnline(Long userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    // ══════════════════ 心跳 ══════════════════

    /**
     * 刷新心跳时间（由 WebSocketHandler 在收到 ping 时调用）
     */
    public void refreshHeartbeat(String sessionId) {
        lastHeartbeatMap.put(sessionId, System.currentTimeMillis());
    }

    /**
     * 每 30 秒扫描一次，踢掉 90 秒无心跳的僵尸连接
     */
    @Scheduled(fixedDelay = 30_000)
    public void evictDeadSessions() {
        long now = System.currentTimeMillis();
        long timeout = 90_000L;
        lastHeartbeatMap.forEach((sessionId, lastTime) -> {
            if (now - lastTime > timeout) {
                sessions.forEach((uid, s) -> {
                    if (s.getId().equals(sessionId)) {
                        log.warn("心跳超时，强制断开: userId={}, sessionId={}", uid, sessionId);
                        closeSession(s, new CloseStatus(4002, "心跳超时"));
                    }
                });
            }
        });
    }

    // ══════════════════ 消息推送 ══════════════════

    /**
     * 异步推送消息给指定用户
     * 调用方（HTTP 请求线程或 WS handler 线程）立即返回，由 pushExecutor 线程池实际发送
     */
    public void sendToUser(Long userId, String message) {
        if (userId == null || message == null) {
            return;
        }

        // 如果当前处于数据库事务中，推送延迟到事务成功提交后再执行。
        // 避免事务后续回滚时，前端已经收到“不存在的消息/状态”。
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSendToUser(userId, message);
                }
            });
            return;
        }

        doSendToUser(userId, message);
    }

    private void doSendToUser(Long userId, String message) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            log.debug("用户 {} 不在线，消息降级为离线（已入库）", userId);
            return;
        }
        log.info("ws推送消息: userId={}, online={}, message={}", userId, session != null && session.isOpen(), message);
        pushExecutor.execute(() -> doSend(userId, session, message));
    }

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
            sessions.remove(userId, session); // 移除僵尸 session
        } finally {
            lock.unlock();
        }
    }

    // ══════════════════ 群聊已读游标（Redis）══════════════════

    public void updateGroupLastRead(String key, Long infoId) {
        redisTemplate.opsForValue().set(GROUP_READ_KEY_PREFIX + key, infoId);
    }

    public Long getGroupLastRead(String key) {
        Object val = redisTemplate.opsForValue().get(GROUP_READ_KEY_PREFIX + key);
        return val == null ? null : Long.parseLong(val.toString());
    }

    // ══════════════════ 工具 ══════════════════

    /**
     * 关闭指定 session（供 Handler 在断线重连时关闭旧连接）
     */
    public void closeSession(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) session.close(status);
        } catch (IOException e) {
            log.warn("关闭 session 失败: sessionId={}", session.getId());
        }
    }
}
