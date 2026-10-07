package com.aitable.controller;

import com.aitable.common.Result;
import com.aitable.service.HealthService;
import com.aitable.vo.HealthVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口。
 *
 * <p>本阶段用它来验证三件事：后端能启动、MySQL 能连上、Redis 能连上；
 * 同时它也是前端第一个调用的接口，用来确认前后端能正常通信。</p>
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /** 最简单的连通性接口，前端启动后调它即可确认后端活着 */
    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("pong");
    }

    /** 详细健康状态：MySQL、Redis 是否可用 */
    @GetMapping("/health")
    public Result<HealthVO> health() {
        return Result.success(healthService.check());
    }
}
