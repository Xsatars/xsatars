package com.aitable.service;

import com.aitable.vo.HealthVO;

/**
 * 健康检查服务。
 */
public interface HealthService {

    /**
     * 检查 MySQL、Redis 等外部依赖是否可用。
     *
     * @return 健康状态信息
     */
    HealthVO check();
}
