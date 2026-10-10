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

    /**
     * 状态版本号：每次游戏状态推进就 +1（单调递增，不会回退）。
     *
     * <p><b>为什么需要它</b>：前端通过<b>两个独立的 STOMP 频道</b>收状态 ——
     * 房间频道收公开事件（{@code PHASE_CHANGE}，里面也带一份完整公开状态），
     * 用户频道收全量状态（{@code GAME_STATE_UPDATE}）。
     * STOMP <b>不保证跨频道的到达顺序</b>，于是可能出现"新的先到、旧的后到" ——
     * 旧快照把新状态覆盖掉，玩家看到的就是"明明是白天，却闪一下狼人正在刀人"。</p>
     *
     * <p>带上版本号之后，前端只接受<b>不比当前更旧</b>的快照，这个问题从根上没了。</p>
     */
    private long stateSeq;

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
     * 发言历史（跨轮保留，给人读的文本）。
     *
     * <p>公开信息，前端可以拿来显示对局回顾；AI 也靠它形成"记忆"，
     * 发言时会引用前面几轮谁说过什么。</p>
     */
    private List<String> speechHistory = new ArrayList<>();

    /** 投票历史（跨轮保留，文本形式，例如"第 1 轮投票：3 号→5 号　（5 号被放逐）"） */
    private List<String> voteHistory = new ArrayList<>();

    /**
     * 夜晚现在轮到哪个角色行动：{@code WOLF} / {@code WITCH} / {@code SEER}；不在夜晚为 null。
     *
     * <p><b>这是公开信息</b> —— 跟真人玩的时候主持人喊"狼人请睁眼"是一个道理，
     * 大家知道现在轮到谁，但不知道他选了谁。前端拿它显示"狼人正在行动…"这种提示条，
     * 图标和配色由前端定，后端只说"轮到哪个角色"。</p>
     */
    private String nightActor;

    /**
     * 当前这一步的截止时间（毫秒时间戳），0 表示不计时。
     *
     * <p>前端拿它显示操作倒计时。</p>
     */
    private long deadline;

    public Long getGameId() {
        return gameId;
    }

    public List<String> getSpeechHistory() {
        return speechHistory;
    }

    public void setSpeechHistory(List<String> speechHistory) {
        this.speechHistory = speechHistory;
    }

    public List<String> getVoteHistory() {
        return voteHistory;
    }

    public void setVoteHistory(List<String> voteHistory) {
        this.voteHistory = voteHistory;
    }

    public String getNightActor() {
        return nightActor;
    }

    public void setNightActor(String nightActor) {
        this.nightActor = nightActor;
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

    public long getStateSeq() {
        return stateSeq;
    }

    public void setStateSeq(long stateSeq) {
        this.stateSeq = stateSeq;
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
