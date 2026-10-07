package com.aitable.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 管理后台 - 数据概览。
 */
public class AdminOverviewVO {

    // ---------- 用户 ----------
    private long userCount;
    private long adminCount;
    private long todayNewUsers;

    // ---------- 房间 ----------
    private long roomCount;
    private long waitingRoomCount;
    private long playingRoomCount;
    private long finishedRoomCount;

    // ---------- 对局 ----------
    private long gameCount;
    private long finishedGameCount;

    /** Redis 里的在线连接数 */
    private long onlineCount;

    /** 系统依赖状态（MySQL、Redis、Java 版本等） */
    private HealthVO system;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt;

    public long getUserCount() {
        return userCount;
    }

    public void setUserCount(long userCount) {
        this.userCount = userCount;
    }

    public long getAdminCount() {
        return adminCount;
    }

    public void setAdminCount(long adminCount) {
        this.adminCount = adminCount;
    }

    public long getTodayNewUsers() {
        return todayNewUsers;
    }

    public void setTodayNewUsers(long todayNewUsers) {
        this.todayNewUsers = todayNewUsers;
    }

    public long getRoomCount() {
        return roomCount;
    }

    public void setRoomCount(long roomCount) {
        this.roomCount = roomCount;
    }

    public long getWaitingRoomCount() {
        return waitingRoomCount;
    }

    public void setWaitingRoomCount(long waitingRoomCount) {
        this.waitingRoomCount = waitingRoomCount;
    }

    public long getPlayingRoomCount() {
        return playingRoomCount;
    }

    public void setPlayingRoomCount(long playingRoomCount) {
        this.playingRoomCount = playingRoomCount;
    }

    public long getFinishedRoomCount() {
        return finishedRoomCount;
    }

    public void setFinishedRoomCount(long finishedRoomCount) {
        this.finishedRoomCount = finishedRoomCount;
    }

    public long getGameCount() {
        return gameCount;
    }

    public void setGameCount(long gameCount) {
        this.gameCount = gameCount;
    }

    public long getFinishedGameCount() {
        return finishedGameCount;
    }

    public void setFinishedGameCount(long finishedGameCount) {
        this.finishedGameCount = finishedGameCount;
    }

    public long getOnlineCount() {
        return onlineCount;
    }

    public void setOnlineCount(long onlineCount) {
        this.onlineCount = onlineCount;
    }

    public HealthVO getSystem() {
        return system;
    }

    public void setSystem(HealthVO system) {
        this.system = system;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
