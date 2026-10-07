package com.aitable.config;

import com.aitable.common.BusinessException;
import com.aitable.common.RedisKeys;
import com.aitable.common.ResultCode;
import com.aitable.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录校验拦截器。
 *
 * <p>流程：从请求头 {@code Authorization: Bearer xxx} 取出 token
 * → 到 Redis 查这个 token 对应哪个用户 → 放进 {@link UserContext}。</p>
 *
 * <p>token 无效或没带就抛 401，由全局异常处理统一返回，
 * Controller 里不需要写任何登录判断。</p>
 */
@Component
public class TokenInterceptor implements HandlerInterceptor {

    /** 请求头名字 */
    public static final String HEADER = "Authorization";

    /** 请求头前缀 */
    public static final String PREFIX = "Bearer ";

    private final StringRedisTemplate stringRedisTemplate;

    public TokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 浏览器的跨域预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        String userId = stringRedisTemplate.opsForValue().get(RedisKeys.token(token));
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        UserContext.setUserId(Long.valueOf(userId));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 一定要清掉，否则线程被复用时会串用户
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        if (header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length()).trim();
        }
        // 没带 Bearer 前缀也接受，方便用 Postman 调试
        return header.trim();
    }
}
