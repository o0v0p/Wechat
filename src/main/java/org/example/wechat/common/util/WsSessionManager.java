package org.example.wechat.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

/**
 * WebSocket Session 管理器
 *
 * 职责：单一——只管 session 存取和消息推送，不涉及任何业务逻辑
 * 目的：切断 ChatWebSocketHandler ↔ ChatServiceImpl 的循环依赖
 *
 * 依赖关系：
 *   WsSessionManager  ← ChatWebSocketHandler（注册/注销 session）
 *   WsSessionManager  ← ChatServiceImpl（推送消息）
 *   WsSessionManager 本身不依赖任何业务 Bean，无循环
 */
@Slf4j
@Component
public class WsSessionManager {

    @Autowired
    @Qualifier("pushExecutor")
    private ExecutorService pushExecutor;

    // userId → session
    private final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 用户级发送锁：防止 isOpen() 与 sendMessage() 之间的竞态
    private final ConcurrentHashMap<Long, ReentrantLock> sendLocks = new ConcurrentHashMap<>();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String GROUP_READ_KEY_PREFIX = "group:lastRead:";

    public void updateGroupLastRead(String key, Long infoId) {
        redisTemplate.opsForValue().set(GROUP_READ_KEY_PREFIX + key, infoId);
    }

    public Long getGroupLastRead(String key) {
        Object val = redisTemplate.opsForValue().get(GROUP_READ_KEY_PREFIX + key);
        return val == null ? null : Long.parseLong(val.toString());
    }

    // ══════════════════ session 注册/注销 ══════════════════
    /**
     * 注册新 session，返回被替换的旧 session（断线重连时由 Handler 负责关闭旧连接）
     */
    public WebSocketSession register(Long userId, WebSocketSession session) {
        sendLocks.putIfAbsent(userId, new ReentrantLock());
        return sessions.put(userId, session);
    }

    /**
     * 注销 session（只移除当前 session，避免重连时误删新 session）
     */
    public void unregister(Long userId, WebSocketSession session) {
        sessions.remove(userId, session);
        // 注意：不移除 sendLocks，锁对象可复用，下次重连无需重建
    }

    /**
     * 判断用户是否在线
     */
    public boolean isOnline(Long userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    // ══════════════════ 消息推送 ══════════════════
    /**
     * 异步推送消息给指定用户
     * 调用方立即返回，实际发送由 pushExecutor 线程池完成
     */
    public void sendToUser(Long userId, String message) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            log.debug("用户 {} 不在线，消息降级为离线（已入库）", userId);
            return;
        }
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
            // 发送失败说明连接已异常，移除僵尸 session
            sessions.remove(userId, session);
        } finally {
            lock.unlock();
        }
    }

    // ══════════════════ 内部访问（供 Handler 使用）══════════════════
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