package com.aitable.config;

import com.aitable.common.BusinessException;
import com.aitable.common.ResultCode;
import com.aitable.common.UserContext;
import com.aitable.common.enums.UserRole;
import com.aitable.entity.User;
import com.aitable.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员权限校验拦截器。
 *
 * <p>只拦 {@code /api/admin/**}。它跑在 {@link TokenInterceptor} 后面，
 * 所以执行到这里时一定已经登录，直接从 {@link UserContext} 取用户ID即可。</p>
 *
 * <p>不是管理员就返回 403，前端会提示"没有权限"。</p>
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;

    public AdminInterceptor(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null || !UserRole.isAdmin(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return true;
    }
}
