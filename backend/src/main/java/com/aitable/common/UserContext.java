package com.aitable.common;

/**
 * 当前登录用户。
 *
 * <p>由 {@code TokenInterceptor} 在请求进入时把 token 解析出来的用户ID放进来，
 * 请求结束时清掉。所以 Service 里可以随时用 {@link #getUserId()} 拿到"现在是谁在操作"，
 * 不用一层层往下传参数。</p>
 *
 * <p>用的是 ThreadLocal：每个请求一个线程，互不干扰。</p>
 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    /** 当前登录用户ID；没有登录时抛业务异常（正常流程下不会发生，因为拦截器已经拦住了） */
    public static Long getUserId() {
        Long userId = CURRENT_USER_ID.get();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
