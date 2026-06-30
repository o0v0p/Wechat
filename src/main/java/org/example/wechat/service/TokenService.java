package org.example.wechat.service;

import lombok.extern.slf4j.Slf4j;
import org.example.wechat.common.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Token 失效服务。
 * 不改数据库字段，使用 Redis 实现：
 * 1. jwt:blacklist:token:{sha256(token)} 让单个 Token 失效；
 * 2. user:tokenInvalidBefore:{userId} 让某用户某时间点之前签发的所有 Token 失效。
 */
@Slf4j
@Service
public class TokenService {

    private static final String TOKEN_BLACKLIST_PREFIX = "jwt:blacklist:token:";
    private static final String USER_TOKEN_INVALID_BEFORE_PREFIX = "user:tokenInvalidBefore:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JwtUtils jwtUtils;

    /** 使当前单个 Token 失效，用于退出登录。使用 SHA256 摘要作为 Redis key。 */
    public void invalidateToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        long ttl = jwtUtils.getRemainingMillis(token);
        if (ttl <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(TOKEN_BLACKLIST_PREFIX + sha256(token), "1", ttl, TimeUnit.MILLISECONDS);
        log.info("Token 已加入黑名单，ttl={}ms", ttl);
    }

    /**
     * 使某用户当前时间点之前签发的所有 Token 失效。
     * 用于注销账号、修改密码等需要踢掉历史登录态的场景。
     */
    public void invalidateAllTokensForUser(Long userId) {
        if (userId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long ttl = Math.max(jwtUtils.getExpirationMillis(), TimeUnit.DAYS.toMillis(7));
        redisTemplate.opsForValue().set(USER_TOKEN_INVALID_BEFORE_PREFIX + userId, now, ttl, TimeUnit.MILLISECONDS);
        log.info("用户全部历史 Token 已失效: userId={}, invalidBefore={}", userId, now);
    }

    /** 校验 Token 是否已经失效。调用前应保证 Token 格式和签名合法。 */
    public boolean isTokenInvalid(String token) {
        if (!StringUtils.hasText(token)) {
            return true;
        }
        Boolean blacklisted = redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + sha256(token));
        if (Boolean.TRUE.equals(blacklisted)) {
            return true;
        }
        Long userId = jwtUtils.getUserIdFromToken(token);
        Date issuedAt = jwtUtils.getIssuedAtDateFromToken(token);
        if (userId == null || issuedAt == null) {
            return true;
        }
        Object invalidBeforeObj = redisTemplate.opsForValue().get(USER_TOKEN_INVALID_BEFORE_PREFIX + userId);
        if (invalidBeforeObj == null) {
            return false;
        }
        long invalidBefore = parseLong(invalidBeforeObj);
        return issuedAt.getTime() <= invalidBefore;
    }

    private long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("生成 Token 摘要失败", e);
        }
    }
}
