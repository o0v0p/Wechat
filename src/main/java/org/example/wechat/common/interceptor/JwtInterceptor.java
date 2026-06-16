package org.example.wechat.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.Result;
import org.example.wechat.common.util.JwtUtils;
import org.example.wechat.common.util.UserContext;
import org.example.wechat.dao.UserMapper;
import org.example.wechat.pojo.entity.BizUser;
import org.example.wechat.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;

@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final TokenService tokenService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public JwtInterceptor(JwtUtils jwtUtils, TokenService tokenService, UserMapper userMapper) {
        this.jwtUtils = jwtUtils;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
        this.objectMapper = new ObjectMapper();
    }

    // 白名单路径
    private static final String[] WHITELIST = {
            "/user/login", "/user/signup", "/user/sendCode",
            "/doc.html", "/user/reset-password",
            "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        log.info("拦截器拦截到请求: {}", path);
        if (!(handler instanceof HandlerMethod)) {
            return true;  // 静态资源直接放行
        }

        // 白名单放行
        for (String white : WHITELIST) {
            if (path.contains(white)) {
                return true;
            }
        }

        // OPTIONS 请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return true;
        }

        // 1. 获取 token（使用 Spring 自带的 StringUtils）
        String token = request.getHeader("Authorization");
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!StringUtils.hasText(token)) {
            token = request.getHeader("token");
        }
        if (!StringUtils.hasText(token)) {
            token = request.getParameter("token");
        }

        // 2. 没有 token，返回未登录 (401)
        if (!StringUtils.hasText(token)) {
            sendErrorResponse(response, 401, "请先登录");
            return false;
        }

        // 3. 解析 token
        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            String username = jwtUtils.getUsernameFromToken(token);
            if (userId == null || username == null) {
                log.warn("userId 或 username 为 null");
                sendErrorResponse(response, 401, "Token 无效");
                return false;
            }
            if (tokenService.isTokenInvalid(token)) {
                log.warn("Token 已失效或已退出: userId={}", userId);
                sendErrorResponse(response, 401, "登录已失效，请重新登录");
                return false;
            }
            BizUser loginUser = userMapper.getByUserIdAny(userId);
            if (loginUser == null) {
                log.warn("Token 对应用户不存在: userId={}", userId);
                sendErrorResponse(response, 401, "用户不存在，请重新登录");
                return false;
            }
            if (loginUser.getIsDeleted() != null && loginUser.getIsDeleted() == 1) {
                log.warn("已注销用户尝试访问接口: userId={}", userId);
                sendErrorResponse(response, 401, "账号已注销，请重新登录");
                return false;
            }

            // 存入 ThreadLocal
            UserContext.setUserId(userId);
            UserContext.setUserName(username);
            log.info("已存入 UserContext: userId={}, username={}", userId, username);

            request.setAttribute("userId", userId);
            request.setAttribute("username", username);
            log.info("JWT 校验通过: userId={}, username={}", userId, username);
            return true;

        } catch (ExpiredJwtException e) {
            log.error("Token 已过期: {}", e.getMessage());
            sendErrorResponse(response, 401, "Token 已过期，请重新登录");
            return false;
        } catch (SignatureException e) {
            log.error("Token 签名无效: {}", e.getMessage());
            sendErrorResponse(response, 401, "Token 签名无效");
            return false;
        } catch (MalformedJwtException e) {
            log.error("Token 格式错误: {}", e.getMessage());
            sendErrorResponse(response, 401, "Token 格式错误");
            return false;
        } catch (Exception e) {
            log.error("Token 解析失败: {}", e.getMessage());
            e.printStackTrace();
            sendErrorResponse(response, 401, "Token 无效");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理 ThreadLocal，防止内存泄漏
        UserContext.remove();
    }

    /**
     * 发送错误响应，设置正确的 HTTP 状态码
     * @param response HttpServletResponse
     * @param httpStatus HTTP 状态码 (401, 403, 500 等)
     * @param message 错误消息
     */
    private void sendErrorResponse(HttpServletResponse response, int httpStatus, String message) {
        try {
            response.setStatus(httpStatus);
            response.setContentType("application/json;charset=UTF-8");

            // 根据 HTTP 状态码选择合适的 Result 方法
            Result<?> result;
            switch (httpStatus) {
                case 401:
                    result = Result.unauthorized(message);
                    break;
                case 403:
                    result = Result.forbidden(message);
                    break;
                case 404:
                    result = Result.notFound(message);
                    break;
                case 409:
                    result = Result.conflict(message);
                    break;
                case 400:
                    result = Result.badRequest(message);
                    break;
                case 500:
                    result = Result.serverError(message);
                    break;
                default:
                    result = Result.error(message);
                    break;
            }
            String json = objectMapper.writeValueAsString(result);
            PrintWriter writer = response.getWriter();
            writer.write(json);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            log.error("响应错误信息失败: {}", e.getMessage());
        }
    }
}