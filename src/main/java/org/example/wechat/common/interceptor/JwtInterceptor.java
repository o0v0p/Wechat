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
import org.example.wechat.common.util.TokenUtils;
import org.example.wechat.common.util.UserContext;
import org.example.wechat.dao.UserMapper;
import org.example.wechat.pojo.entity.BizUser;
import org.example.wechat.service.TokenService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;

@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final TokenService tokenService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public JwtInterceptor(JwtUtils jwtUtils, TokenService tokenService, UserMapper userMapper) {
        this.jwtUtils = jwtUtils;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return true;
        }

        String token = TokenUtils.extractToken(request);
        if (!StringUtils.hasText(token)) {
            sendErrorResponse(response, 401, "请先登录");
            return false;
        }

        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            String username = jwtUtils.getUsernameFromToken(token);
            if (userId == null || username == null) {
                sendErrorResponse(response, 401, "Token 无效");
                return false;
            }
            if (tokenService.isTokenInvalid(token)) {
                log.warn("Token 已失效，userId={}", userId);
                sendErrorResponse(response, 401, "登录已失效，请重新登录");
                return false;
            }
            BizUser loginUser = userMapper.getByUserIdAny(userId);
            if (loginUser == null) {
                log.warn("Token 对应用户不存在，userId={}", userId);
                sendErrorResponse(response, 401, "用户不存在，请重新登录");
                return false;
            }
            if (Integer.valueOf(1).equals(loginUser.getIsDeleted())) {
                log.warn("已注销用户访问接口，userId={}", userId);
                sendErrorResponse(response, 401, "账号已注销，请重新登录");
                return false;
            }

            UserContext.setUserId(userId);
            UserContext.setUserName(username);
            request.setAttribute("userId", userId);
            request.setAttribute("username", username);
            return true;
        } catch (ExpiredJwtException e) {
            sendErrorResponse(response, 401, "Token 已过期，请重新登录");
            return false;
        } catch (SignatureException e) {
            sendErrorResponse(response, 401, "Token 签名无效");
            return false;
        } catch (MalformedJwtException e) {
            sendErrorResponse(response, 401, "Token 格式错误");
            return false;
        } catch (Exception e) {
            log.warn("Token 解析失败: {}", e.getMessage());
            sendErrorResponse(response, 401, "Token 无效");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.remove();
    }

    private void sendErrorResponse(HttpServletResponse response, int httpStatus, String message) {
        try {
            response.setStatus(httpStatus);
            response.setContentType("application/json;charset=UTF-8");
            Result<?> result = switch (httpStatus) {
                case 400 -> Result.badRequest(message);
                case 401 -> Result.unauthorized(message);
                case 403 -> Result.forbidden(message);
                case 404 -> Result.notFound(message);
                case 409 -> Result.conflict(message);
                case 500 -> Result.serverError(message);
                default -> Result.error(message);
            };
            String json = objectMapper.writeValueAsString(result);
            PrintWriter writer = response.getWriter();
            writer.write(json);
            writer.flush();
        } catch (Exception e) {
            log.error("写入鉴权错误响应失败: {}", e.getMessage());
        }
    }
}
