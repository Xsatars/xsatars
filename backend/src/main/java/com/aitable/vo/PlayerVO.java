package com.aitable.vo;

/**
 * 房间里的一位玩家（返回给前端）。
 *
 * <p>只含公开信息——角色、身份一律不在这里。</p>
 */
public class PlayerVO {

    /** players 表的主键（离开房间等操作要用它） */
    private Long playerId;

    private Long userId;

    private String nickname;

    private String avatar;

    /** 座位号 1..6 */
    private Integer seatNo;

    private boolean ready;

    /** 是不是房主 */
    private boolean owner;

    /** 是否在线（掉线后房间里会显示"离线"） */
    private boolean online = true;

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Integer getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(Integer seatNo) {
        this.seatNo = seatNo;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public boolean isOwner() {
        return owner;
    }

    public void setOwner(boolean owner) {
        this.owner = owner;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }
}
