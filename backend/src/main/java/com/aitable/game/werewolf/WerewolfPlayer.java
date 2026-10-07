package com.aitable.game.werewolf;

import com.aitable.common.enums.Role;

/**
 * 一局游戏里的玩家（坐在某个座位上的人 + 他的身份）。
 *
 * <p>这个对象属于<b>完整游戏状态</b>，只在引擎内部使用，绝不下发给前端。</p>
 */
public class WerewolfPlayer {

    /** 座位号 1..6 */
    private final int seatNo;

    private final Long userId;

    private final String nickname;

    /** 身份 */
    private Role role;

    /** 是否存活 */
    private boolean alive = true;

    /** 本轮是否已经发过言 */
    private boolean spoke = false;

    /** 今晚是否已经行动过（狼刀 / 查验 / 女巫用药） */
    private boolean actedTonight = false;

    /** 是否在线；掉线的玩家会被自动跳过，不用干等超时 */
    private boolean online = true;

    /** 头像地址；为空表示还没设置头像 */
    private String avatar;

    public WerewolfPlayer(int seatNo, Long userId, String nickname) {
        this.seatNo = seatNo;
        this.userId = userId;
        this.nickname = nickname;
    }

    public int getSeatNo() {
        return seatNo;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public boolean isSpoke() {
        return spoke;
    }

    public void setSpoke(boolean spoke) {
        this.spoke = spoke;
    }

    public boolean isActedTonight() {
        return actedTonight;
    }

    public void setActedTonight(boolean actedTonight) {
        this.actedTonight = actedTonight;
    }

    /** 是不是狼人 */
    public boolean isWolf() {
        return role != null && role.isWolf();
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }
}
