package com.aitable.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置：注册拦截器。
 *
 * <p>执行顺序就是注册顺序：先校验登录（{@link TokenInterceptor}），
 * 再校验管理员权限（{@link AdminInterceptor}）。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TokenInterceptor tokenInterceptor;
    private final AdminInterceptor adminInterceptor;

    public WebMvcConfig(TokenInterceptor tokenInterceptor, AdminInterceptor adminInterceptor) {
        this.tokenInterceptor = tokenInterceptor;
        this.adminInterceptor = adminInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1) 登录校验：除下面几个接口外，/api/** 都要带 token
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/users/login",
                        "/api/users/register",
                        // 忘记密码：人家就是登不上才来的，当然不能要求登录
                        "/api/users/reset-password",
                        "/api/health",
                        "/api/ping"
                );

        // 2) 管理员校验：只拦管理后台接口
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**");
    }
}
