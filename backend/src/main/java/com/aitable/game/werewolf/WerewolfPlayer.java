package com.aitable.game.werewolf;

import com.aitable.common.enums.Role;
import com.aitable.game.ai.AiPersona;

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

    /**
     * 怎么出局的：{@code WOLF}（被狼刀）/ {@code POISON}（被女巫毒杀）
     * / {@code VOTE}（被投票放逐）/ {@code HUNTER}（被猎人开枪带走）。存活时为 null。
     *
     * <p><b>为什么要专门记这个</b>：猎人被狼刀死、被票出局<b>都能开枪</b>，
     * 但被女巫毒死<b>不能</b> —— 判定就靠这个字段，不能只看"他死了"。</p>
     */
    private String deathReason;

    /** 本轮是否已经发过言 */
    private boolean spoke = false;

    /** 今晚是否已经行动过（狼刀 / 查验 / 女巫用药） */
    private boolean actedTonight = false;

    /** 是否在线；掉线的玩家会被自动跳过，不用干等超时 */
    private boolean online = true;

    /** 头像地址；为空表示还没设置头像 */
    private String avatar;

    /** 是不是 AI 玩家（真人由用户操作，AI 由程序决策） */
    private boolean ai;

    /**
     * AI 的人设（名字、口吻、行为倾向）。
     *
     * <p>规则 AI 用它来决定"多激进"，大模型 AI 用它来写提示词。真人玩家这里是 null。</p>
     */
    private AiPersona persona;

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

    public String getDeathReason() {
        return deathReason;
    }

    public void setDeathReason(String deathReason) {
        this.deathReason = deathReason;
    }

    /** 出局并记下原因（存活状态和原因必须一起改，免得出现"死了但不知道咋死的"） */
    public void die(String reason) {
        this.alive = false;
        this.deathReason = reason;
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

    public boolean isAi() {
        return ai;
    }

    public void setAi(boolean ai) {
        this.ai = ai;
    }

    public AiPersona getPersona() {
        return persona;
    }

    public void setPersona(AiPersona persona) {
        this.persona = persona;
    }
}
