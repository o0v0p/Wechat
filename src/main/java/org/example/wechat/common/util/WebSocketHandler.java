package org.example.wechat.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.constants.ReceiverTypeConstant;
import org.example.wechat.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private WsSessionManager sessionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatService chatService;

    // ══════════════════ 连接生命周期 ══════════════════

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            log.warn("afterConnectionEstablished：attributes 中无 userId，关闭连接");
            closeQuietly(session, new CloseStatus(4001, "认证信息缺失"));
            return;
        }

        // 注册到 WsSessionManager（含心跳初始化），返回旧 session 供断线重连处理
        WebSocketSession oldSession = sessionManager.register(userId, session);
        if (oldSession != null && oldSession.isOpen() && !oldSession.getId().equals(session.getId())) {
            log.info("用户 {} 断线重连，关闭旧 session={}", userId, oldSession.getId());
            sessionManager.closeSession(oldSession, new CloseStatus(4000, "连接已在其他端建立"));
        }

        log.info("WebSocket 连接成功: userId={}", userId);
        pushOfflineNotification(userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.unregister(userId, session);
            log.info("WebSocket 连接关闭: userId={}, status={}", userId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = (Long) session.getAttributes().get("userId");
        log.error("WebSocket 传输错误: userId={}, error={}", userId, exception.getMessage());
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    // ══════════════════ 消息 dispatch ══════════════════

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = (Long) session.getAttributes().get("userId");
        String payload = message.getPayload();
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText("");

            switch (type) {
                case "ping":
                    // 刷新心跳，pong 回包
                    sessionManager.refreshHeartbeat(session.getId());
                    sendRaw(session, "{\"type\":\"pong\"}");
                    break;

                case "read":
                    if (userId == null) break;
                    long targetId   = node.path("targetId").asLong(-1);
                    int sessionType = node.path("sessionType").asInt(-1);
                    if (targetId < 0 || sessionType < 0) {
                        log.warn("read 消息参数缺失: {}", payload);
                        break;
                    }
                    handleRead(userId, targetId, sessionType);
                    break;

                default:
                    log.warn("未知消息类型: {}, userId={}", type, userId);
            }
        } catch (Exception e) {
            log.error("处理 WebSocket 消息异常: userId={}, error={}", userId, e.getMessage());
            sendRaw(session, "{\"type\":\"error\",\"message\":\"消息处理失败\"}");
        }
    }

    /**
     * 处理已读上报。
     *
     * 方案A：WebSocket handler 线程无 JwtInterceptor 上下文，调用 Service 前手动
     * 将 userId set 进 UserContext，调用完立即 remove，避免线程复用时的上下文污染。
     * Service 层接口签名不变，HTTP 和 WebSocket 两侧行为完全一致。
     */
    private void handleRead(Long userId, long targetId, int sessionType) {
        try {
            UserContext.setUserId(userId);
            chatService.markMessagesAsRead(targetId, sessionType);
        } finally {
            UserContext.remove(); // 必须在 finally 中清理，防止异常时遗留
        }
    }

    // ══════════════════ 工具方法 ══════════════════

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
}
