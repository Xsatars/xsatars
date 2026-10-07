package com.aitable;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Table 后端启动类。
 *
 * <p>当前阶段（V0.1-基础架构）只做：Spring Boot 启动、连接 MySQL、连接 Redis、WebSocket 端点就绪。
 * 狼人杀游戏规则、房间系统、AI 玩家等都还没有实现。</p>
 */
@SpringBootApplication
@MapperScan("com.aitable.mapper")
public class AiTableApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTableApplication.class, args);
    }
}
