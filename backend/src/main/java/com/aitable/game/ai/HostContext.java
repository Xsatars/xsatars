package com.aitable.game.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 主持人解说要用到的"局面素材"。
 *
 * <p><b>故意只给这些</b>：主持人是个站在上帝视角说话的角色，但他说的话是
 * <b>全场人都能听到的</b> —— 所以给他的素材必须是"已经公开的信息"，
 * 绝不能包含任何人的身份（游戏结束那一句除外，那时候身份本来就公开了）。</p>
 */
public class HostContext {

    /** 什么时候说的：DAWN=天亮 / VOTE=放逐结果 / GAME_END=分出胜负 */
    private String moment;

    private int round;

    /** 阶段名字，比如"白天·发言" */
    private String phaseLabel;

    /** 昨夜出局的人，例如 ["3 号 阿豆"]；平安夜时是空的 */
    private List<String> deaths = new ArrayList<>();

    /** 被投票放逐的人，例如"3 号 阿豆"；没人出局时为 null */
    private String exiled;

    /** 胜方："WOLF" / "GOOD"；只有游戏结束时才有 */
    private String winnerCamp;

    /** 现在活着的人，例如"1、2、5、6 号" */
    private String aliveSeats;

    /** 已经淘汰的人 */
    private String deadSeats;

    /**
     * 兜底文案。
     *
     * <p>大模型调不通的时候直接用这句，保证主持人**永远不会沉默**。</p>
     */
    private String fallbackText;

    public String getMoment() {
        return moment;
    }

    public void setMoment(String moment) {
        this.moment = moment;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public String getPhaseLabel() {
        return phaseLabel;
    }

    public void setPhaseLabel(String phaseLabel) {
        this.phaseLabel = phaseLabel;
    }

    public List<String> getDeaths() {
        return deaths;
    }

    public void setDeaths(List<String> deaths) {
        this.deaths = deaths == null ? new ArrayList<>() : deaths;
    }

    public String getExiled() {
        return exiled;
    }

    public void setExiled(String exiled) {
        this.exiled = exiled;
    }

    public String getWinnerCamp() {
        return winnerCamp;
    }

    public void setWinnerCamp(String winnerCamp) {
        this.winnerCamp = winnerCamp;
    }

    public String getAliveSeats() {
        return aliveSeats;
    }

    public void setAliveSeats(String aliveSeats) {
        this.aliveSeats = aliveSeats;
    }

    public String getDeadSeats() {
        return deadSeats;
    }

    public void setDeadSeats(String deadSeats) {
        this.deadSeats = deadSeats;
    }

    public String getFallbackText() {
        return fallbackText;
    }

    public void setFallbackText(String fallbackText) {
        this.fallbackText = fallbackText;
    }
}
