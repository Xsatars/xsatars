package com.aitable.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密配置。
 *
 * <p>用 BCrypt：同一条密码每次加密结果都不同（自带随机盐），
 * 校验时用 {@code matches()} 比对。</p>
 *
 * <p>注意：项目只用了 Spring Security 的加密工具类，没有引入它的登录过滤器，
 * 所以不会影响现有的接口访问方式。</p>
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
