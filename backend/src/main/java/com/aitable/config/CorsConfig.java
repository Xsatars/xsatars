package com.aitable.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置。
 *
 * <p>开发阶段前端跑在 Vite 的 5173 端口，后端跑在 8080 端口，属于跨域请求，
 * 所以这里放开跨域。前端也可以走 Vite 代理（见 frontend/vite.config.ts），
 * 两种方式留一种都能跑通。</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 开发环境放开；上线时应改成具体域名，例如 http://your-domain.com
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
