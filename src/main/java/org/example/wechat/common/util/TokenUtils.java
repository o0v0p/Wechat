package org.example.wechat.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public final class TokenUtils {

    private TokenUtils() {
    }

    public static String extractToken(HttpServletRequest request) {
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
        return token;
    }
}
