package com.aitable.game.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 公共游戏状态：<b>房间里所有人都能看到的那部分</b>。
 *
 * <p>阶段、轮次、谁活着、谁得了几票、谁赢了——就这些。
 * 里面绝对不含任何人的身份信息。</p>
 */
public class PublicGameState {

    private Long gameId;

    private Long roomId;

    /** 阶段代号，见 {@link GamePhase} */
    private String phase;

    /** 阶段中文名，前端直接显示 */
    private String phaseLabel;

    /** 第几轮（一个夜晚+一个白天算一轮） */
    private int round;

    /** 存活人数 */
    private int aliveCount;

    /** 各座位的公开信息 */
    private List<SeatView> seats = new ArrayList<>();

    /** 已经结束时有值：WOLF=狼人胜 / GOOD=好人胜 */
    private String winnerCamp;

    /** 最近几条公开提示（例如"天亮了，昨晚是平安夜"） */
    private List<String> recentMessages = new ArrayList<>();

    /**
     * 当前这一步的截止时间（毫秒时间戳），0 表示不计时。
     *
     * <p>前端拿它显示操作倒计时。</p>
     */
    private long deadline;

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getPhaseLabel() {
        return phaseLabel;
    }

    public void setPhaseLabel(String phaseLabel) {
        this.phaseLabel = phaseLabel;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public int getAliveCount() {
        return aliveCount;
    }

    public void setAliveCount(int aliveCount) {
        this.aliveCount = aliveCount;
    }

    public List<SeatView> getSeats() {
        return seats;
    }

    public void setSeats(List<SeatView> seats) {
        this.seats = seats;
    }

    public String getWinnerCamp() {
        return winnerCamp;
    }

    public void setWinnerCamp(String winnerCamp) {
        this.winnerCamp = winnerCamp;
    }

    public List<String> getRecentMessages() {
        return recentMessages;
    }

    public void setRecentMessages(List<String> recentMessages) {
        this.recentMessages = recentMessages;
    }

    public long getDeadline() {
        return deadline;
    }

    public void setDeadline(long deadline) {
        this.deadline = deadline;
    }
}
