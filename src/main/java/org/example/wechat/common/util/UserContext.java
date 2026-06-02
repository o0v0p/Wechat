package org.example.wechat.common.util;

import com.alibaba.ttl.TransmittableThreadLocal;  // ← 新增 import

public class UserContext {

    private static final TransmittableThreadLocal<Long> USER_ID_HOLDER
            = new TransmittableThreadLocal<>();

    private static final TransmittableThreadLocal<String> USERNAME_HOLDER
            = new TransmittableThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static void setUserName(String userName) {
        USERNAME_HOLDER.set(userName);
    }

    public static Long getUserId() {
        Long userId = USER_ID_HOLDER.get();
        if (userId == null) {
            throw new IllegalStateException("用户未登录");
        }
        return userId;
    }

    public static String getUsername() {
        String username = USERNAME_HOLDER.get();
        if (username == null) {
            throw new IllegalStateException("用户未登录");
        }
        return username;
    }

    public static void remove() {
        USER_ID_HOLDER.remove();
        USERNAME_HOLDER.remove();
    }
}