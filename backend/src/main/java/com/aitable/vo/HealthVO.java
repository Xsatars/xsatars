package com.aitable.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 健康检查返回给前端的数据。
 */
public class HealthVO {

    /** UP：一切正常；DOWN：有依赖连不上 */
    private String status;

    private String appName;

    private String javaVersion;

    // ---------- MySQL ----------
    private boolean mysqlConnected;
    private String mysqlVersion;
    private String mysqlDatabase;
    private String mysqlError;

    // ---------- Redis ----------
    private boolean redisConnected;
    private String redisVersion;
    private String redisError;

    /** 检查时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkedAt;

    public HealthVO() {
        this.checkedAt = LocalDateTime.now();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    public boolean isMysqlConnected() {
        return mysqlConnected;
    }

    public void setMysqlConnected(boolean mysqlConnected) {
        this.mysqlConnected = mysqlConnected;
    }

    public String getMysqlVersion() {
        return mysqlVersion;
    }

    public void setMysqlVersion(String mysqlVersion) {
        this.mysqlVersion = mysqlVersion;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public void setMysqlDatabase(String mysqlDatabase) {
        this.mysqlDatabase = mysqlDatabase;
    }

    public String getMysqlError() {
        return mysqlError;
    }

    public void setMysqlError(String mysqlError) {
        this.mysqlError = mysqlError;
    }

    public boolean isRedisConnected() {
        return redisConnected;
    }

    public void setRedisConnected(boolean redisConnected) {
        this.redisConnected = redisConnected;
    }

    public String getRedisVersion() {
        return redisVersion;
    }

    public void setRedisVersion(String redisVersion) {
        this.redisVersion = redisVersion;
    }

    public String getRedisError() {
        return redisError;
    }

    public void setRedisError(String redisError) {
        this.redisError = redisError;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }
}
