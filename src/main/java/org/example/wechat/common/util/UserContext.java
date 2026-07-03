package org.example.wechat.common.util;

public class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private static final ThreadLocal<String> USERNAME_HOLDER = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static void setUserName(String userName) {
        USERNAME_HOLDER.set(userName);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static String getUsername() {
        return USERNAME_HOLDER.get();
    }

    public static void remove() {
        USER_ID_HOLDER.remove();
        USERNAME_HOLDER.remove();
    }
}
