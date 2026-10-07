package com.aitable.service.impl;

import com.aitable.service.HealthService;
import com.aitable.vo.HealthVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Properties;

/**
 * 健康检查服务实现。
 *
 * <p>MySQL 用一条 {@code SELECT VERSION()} 验证；Redis 用一次真实的写入+读取验证
 * （比只 ping 一次更能说明问题：说明读写都正常）。</p>
 */
@Service
public class HealthServiceImpl implements HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthServiceImpl.class);

    /** 健康检查写入 Redis 的 key */
    private static final String REDIS_HEALTH_KEY = "ai-table:health";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public HealthServiceImpl(JdbcTemplate jdbcTemplate, StringRedisTemplate stringRedisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public HealthVO check() {
        HealthVO vo = new HealthVO();
        vo.setAppName("ai-table");
        vo.setJavaVersion(System.getProperty("java.version"));

        checkMysql(vo);
        checkRedis(vo);

        vo.setStatus(vo.isMysqlConnected() && vo.isRedisConnected() ? "UP" : "DOWN");
        return vo;
    }

    private void checkMysql(HealthVO vo) {
        try {
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            vo.setMysqlConnected(true);
            vo.setMysqlVersion(version);
            vo.setMysqlDatabase(database);
        } catch (Exception e) {
            log.error("MySQL 连接检查失败", e);
            vo.setMysqlConnected(false);
            vo.setMysqlError(e.getMessage());
        }
    }

    private void checkRedis(HealthVO vo) {
        try {
            String value = "ok-" + System.currentTimeMillis();
            stringRedisTemplate.opsForValue().set(REDIS_HEALTH_KEY, value, Duration.ofMinutes(5));
            String readBack = stringRedisTemplate.opsForValue().get(REDIS_HEALTH_KEY);
            vo.setRedisConnected(value.equals(readBack));
            vo.setRedisVersion(readRedisVersion());
        } catch (Exception e) {
            log.error("Redis 连接检查失败", e);
            vo.setRedisConnected(false);
            vo.setRedisError(e.getMessage());
        }
    }

    /** 从 Redis INFO 命令的结果里取版本号，取不到就返回 null（不影响健康检查结论） */
    private String readRedisVersion() {
        try {
            Properties info = stringRedisTemplate.execute(
                    (RedisCallback<Properties>) connection -> connection.serverCommands().info("server"));
            return info == null ? null : info.getProperty("redis_version");
        } catch (Exception e) {
            log.warn("读取 Redis 版本失败: {}", e.getMessage());
            return null;
        }
    }
}
