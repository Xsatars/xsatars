package com.aitable.game.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 让 AI"复盘自己这局"时给的素材。
 *
 * <p>这里是<b>局后</b>，身份已经全部公开了，所以可以放心把角色、胜负都给它 ——
 * 复盘的价值就在于"知道真相之后再回头看"。</p>
 *
 * <p>复盘会存进 {@code game_records.reflection}，下一局作为跨局记忆喂给同一个 AI，
 * 所以它能说出"上一局我不该跟票"这种话 —— 这就是"AI 会进步"。</p>
 */
public class ReflectContext {

    private int seatNo;
    private String nickname;

    /** 人设描述（决定了它用什么口吻复盘） */
    private String personaStyle;

    /** 自己这局是什么角色，如"预言家" */
    private String roleLabel;

    /** 自己属于哪个阵营：WOLF / GOOD */
    private String camp;

    /** 赢了没有 */
    private boolean win;

    /** 活到最后没有 */
    private boolean survived;

    /** 一共打了几轮 */
    private int rounds;

    /** 本局的发言记录（用来回顾"我当时说了什么"） */
    private List<String> speeches = new ArrayList<>();

    /** 本局的投票记录 */
    private List<String> votes = new ArrayList<>();

    /** 出局原因，如"第 2 轮被投票放逐"；活到最后则为 null */
    private String deathReason;

    public int getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(int seatNo) {
        this.seatNo = seatNo;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPersonaStyle() {
        return personaStyle;
    }

    public void setPersonaStyle(String personaStyle) {
        this.personaStyle = personaStyle;
    }

    public String getRoleLabel() {
        return roleLabel;
    }

    public void setRoleLabel(String roleLabel) {
        this.roleLabel = roleLabel;
    }

    public String getCamp() {
        return camp;
    }

    public void setCamp(String camp) {
        this.camp = camp;
    }

    public boolean isWin() {
        return win;
    }

    public void setWin(boolean win) {
        this.win = win;
    }

    public boolean isSurvived() {
        return survived;
    }

    public void setSurvived(boolean survived) {
        this.survived = survived;
    }

    public int getRounds() {
        return rounds;
    }

    public void setRounds(int rounds) {
        this.rounds = rounds;
    }

    public List<String> getSpeeches() {
        return speeches;
    }

    public void setSpeeches(List<String> speeches) {
        this.speeches = speeches == null ? new ArrayList<>() : speeches;
    }

    public List<String> getVotes() {
        return votes;
    }

    public void setVotes(List<String> votes) {
        this.votes = votes == null ? new ArrayList<>() : votes;
    }

    public String getDeathReason() {
        return deathReason;
    }

    public void setDeathReason(String deathReason) {
        this.deathReason = deathReason;
    }
}
