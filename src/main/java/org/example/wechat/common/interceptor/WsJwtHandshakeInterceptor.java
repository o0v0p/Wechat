package org.example.wechat.common.interceptor;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器：在 HTTP 升级为 WebSocket 之前做 JWT 校验
 *
 * 执行时机：WebSocket 握手的 HTTP 请求阶段，早于 afterConnectionEstablished
 * 作用：
 *   1. 从 URL 参数或请求头中取 token，验证合法性
 *   2. 验证通过 → 把 userId/username 存入 attributes，后续 session.getAttributes() 可取到
 *   3. 验证失败 → 返回 false，握手被拒绝，连接不会建立
 *
 * 与 JwtInterceptor 的区别：
 *   JwtInterceptor 是 Spring MVC HandlerInterceptor，只拦截 HandlerMethod 类型的请求，
 *   WebSocket 握手的 handler 是 WebSocketHttpRequestHandler，会被直接放行。
 *   本拦截器通过 WebSocketConfigurer.addInterceptors() 注册，专门作用于 WebSocket 握手。
 */
@Slf4j
@Component
public class WsJwtHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 握手前：校验 Token
     * 返回 true  → 握手继续，连接建立
     * 返回 false → 握手被拒绝，客户端收到 403
     */
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        // 只能从 URL 参数或请求头拿 token（WebSocket 握手不支持自定义请求体）
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            log.warn("WebSocket 握手拒绝：缺少 token，uri={}", request.getURI());
            return false;
        }
        try {
            Long userId   = jwtUtils.getUserIdFromToken(token);
            String username = jwtUtils.getUsernameFromToken(token);
            if (userId == null || username == null) {
                log.warn("WebSocket 握手拒绝：token claims 为空，uri={}", request.getURI());
                return false;
            }
            // 把用户信息存入 attributes
            attributes.put("userId",   userId);
            attributes.put("username", username);
            log.info("WebSocket 握手通过：userId={}, username={}", userId, username);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("WebSocket 握手拒绝：Token 已过期，uri={}", request.getURI());
            return false;
        } catch (SignatureException | MalformedJwtException e) {
            log.warn("WebSocket 握手拒绝：Token 非法，uri={}", request.getURI());
            return false;
        } catch (Exception e) {
            log.error("WebSocket 握手拒绝：Token 解析异常，uri={}, error={}", request.getURI(), e.getMessage());
            return false;
        }
    }

    /**
     * 握手后（连接已建立）：本项目不需要做额外处理
     */
    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // 无需处理
    }

    /**
     * 从请求 URL 参数或请求头中提取 token
     * WebSocket 连接：ws://host/ws?token=xxx
     * 或者请求头：Authorization: Bearer xxx
     */
    private String extractToken(ServerHttpRequest request) {
        // 优先从 URL 参数取
        String query = request.getURI().getQuery();
        if (StringUtils.hasText(query)) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && "token".equals(pair[0])) {
                    return pair[1];
                }
            }
        }
        // 再从请求头取（Authorization: Bearer xxx）
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String authHeader = servletRequest.getServletRequest().getHeader("Authorization");
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
            // 兼容直接放 token 头
            String tokenHeader = servletRequest.getServletRequest().getHeader("token");
            if (StringUtils.hasText(tokenHeader)) {
                return tokenHeader;
            }
        }
        return null;
    }
}