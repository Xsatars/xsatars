package com.aitable.game.core;

/**
 * 一个座位在<b>公共视野</b>里的样子。
 *
 * <p>注意这里<b>没有角色字段</b>——身份是私人信息，
 * 只会出现在 {@link PlayerPrivateState} 里。
 * 所以就算前端把整个公共状态打印出来，也看不到任何人的身份。</p>
 */
public class SeatView {

    private int seatNo;

    private Long userId;

    private String nickname;

    /** 是否存活 */
    private boolean alive;

    /** 是否正在发言 */
    private boolean speaking;

    /** 当前已获得的票数（投票阶段用） */
    private int voteCount;

    /** 本阶段是否已经行动/投票过 */
    private boolean acted;

    /** 玩家是否在线（掉线的玩家会被自动跳过） */
    private boolean online = true;

    /** 头像地址；为空表示还没设置头像（界面上显示默认空白头像） */
    private String avatar;

    /** 是不是 AI 玩家（前端会加个 🤖 标记） */
    private boolean ai;

    /** AI 的性格标签，比如"急脾气"（真人这里是 null） */
    private String aiPersonaTag;

    public int getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(int seatNo) {
        this.seatNo = seatNo;
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

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public boolean isSpeaking() {
        return speaking;
    }

    public void setSpeaking(boolean speaking) {
        this.speaking = speaking;
    }

    public int getVoteCount() {
        return voteCount;
    }

    public void setVoteCount(int voteCount) {
        this.voteCount = voteCount;
    }

    public boolean isActed() {
        return acted;
    }

    public void setActed(boolean acted) {
        this.acted = acted;
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

    public boolean isAi() {
        return ai;
    }

    public void setAi(boolean ai) {
        this.ai = ai;
    }

    public String getAiPersonaTag() {
        return aiPersonaTag;
    }

    public void setAiPersonaTag(String aiPersonaTag) {
        this.aiPersonaTag = aiPersonaTag;
    }
}
