package com.aitable.game.werewolf;

import com.aitable.common.enums.Camp;
import com.aitable.common.enums.Role;
import com.aitable.game.core.GamePhase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 狼人杀的<b>完整游戏状态</b>。
 *
 * <p>这是"上帝视角"的数据：谁是什么身份、昨晚谁刀了谁、女巫还有没有药，全在这里。
 * 它<b>只在引擎内部流转</b>，永远不会被直接发给前端——
 * 前端拿到的是 {@code PublicGameState} + {@code PlayerPrivateState}
 * （见 {@link WerewolfViews} 怎么按座位过滤）。</p>
 */
public class WerewolfState {

    private Long gameId;

    private Long roomId;

    private GamePhase phase = GamePhase.WAITING;

    /** 第几轮（一个夜晚 + 一个白天算一轮） */
    private int round = 0;

    /** 所有玩家，按座位号排序 */
    private final List<WerewolfPlayer> players = new ArrayList<>();

    // ==================== 夜晚 ====================

    /** 狼人各自的刀人选择：狼座位 → 目标座位 */
    private final Map<Integer, Integer> wolfVotes = new HashMap<>();

    /** 狼队最终确定的刀口（所有狼投完之后结算） */
    private Integer wolfTarget;

    /** 狼队是否已经做出决定（超时时"空刀"也算已决定） */
    private boolean wolfDecided = false;

    /** 预言家的查验记录：被查验的座位 → 是不是狼（这个只在预言家自己的视图里出现） */
    private final Map<Integer, Boolean> seerResults = new LinkedHashMap<>();

    /** 本晚预言家查了谁 */
    private Integer seerTarget;

    private boolean seerActed = false;

    /** 女巫的两瓶药（整局各一次） */
    private boolean witchSaveUsed = false;
    private boolean witchPoisonUsed = false;

    /** 本晚女巫是否已经做过决定（用药或放弃） */
    private boolean witchActed = false;

    /** 本晚女巫救的座位 */
    private Integer witchSavedSeat;

    /** 本晚女巫毒的座位 */
    private Integer witchPoisonSeat;

    /** 本晚死亡的座位 */
    private final List<Integer> nightDeaths = new ArrayList<>();

    /** 夜晚进行到哪一步：狼人 → 女巫 → 预言家（只在引擎内部和私有提示里用） */
    private NightStep nightStep = NightStep.WOLF;

    /**
     * 昨夜死亡的座位。
     *
     * <p>下一白天要让这些人先说「遗言」，说完才轮到存活玩家发言。只保留一晚，用完就清。</p>
     */
    private final List<Integer> lastNightDeaths = new ArrayList<>();

    // ==================== 白天 ====================

    /** 发言顺序（存活座位，按座位号从小到大） */
    private final List<Integer> speakOrder = new ArrayList<>();

    /** 当前轮到第几个发言 */
    private int speakIndex = 0;

    /** 已经发出的发言：{seatNo, nickname, text} */
    private final List<Map<String, Object>> speeches = new ArrayList<>();

    /** 白天投票：投票者座位 → 目标座位 */
    private final Map<Integer, Integer> dayVotes = new HashMap<>();

    /**
     * 投票阶段弃权的座位（掉线、或超时没投）。
     *
     * <p>弃权的人不算票，但必须记下来——否则"所有人都投完了"这个条件永远不成立，
     * 对局就会卡在投票阶段。</p>
     */
    private final Set<Integer> abstainedSeats = new HashSet<>();

    /** 被放逐的座位（本轮没人出局时为 null） */
    private Integer exiledSeat;

    // ==================== 其他 ====================

    /** 最近几条公开提示 */
    private final List<String> messages = new ArrayList<>();

    /** 胜方阵营（未结束为 null） */
    private String winnerCamp;

    /**
     * 当前这一步的截止时间（毫秒时间戳）。
     *
     * <p>只要有人在操作就会往后推；推到超过当前时间就说明"卡住了"，
     * 由超时任务替他做默认处理（跳过 / 空刀 / 弃权），免得整局停在这里。</p>
     */
    private long deadline = 0;

    public WerewolfState(Long gameId, Long roomId) {
        this.gameId = gameId;
        this.roomId = roomId;
    }

    // ==================== 查询辅助 ====================

    public WerewolfPlayer findBySeat(int seatNo) {
        return players.stream().filter(p -> p.getSeatNo() == seatNo).findFirst().orElse(null);
    }

    public boolean isAlive(int seatNo) {
        WerewolfPlayer player = findBySeat(seatNo);
        return player != null && player.isAlive();
    }

    /** 存活玩家 */
    public List<WerewolfPlayer> alivePlayers() {
        return players.stream().filter(WerewolfPlayer::isAlive).collect(Collectors.toList());
    }

    /** 存活的狼 */
    public List<WerewolfPlayer> aliveWolves() {
        return players.stream().filter(p -> p.isAlive() && p.isWolf()).collect(Collectors.toList());
    }

    /** 存活的好人 */
    public List<WerewolfPlayer> aliveGood() {
        return players.stream().filter(p -> p.isAlive() && !p.isWolf()).collect(Collectors.toList());
    }

    /** 某个阵营的存活人数 */
    public int countAliveByCamp(Camp camp) {
        return (int) players.stream()
                .filter(WerewolfPlayer::isAlive)
                .filter(p -> p.getRole() != null && p.getRole().getCamp() == camp)
                .count();
    }

    /** 找出唯一一个还活着的某角色（没有就返回 null） */
    public WerewolfPlayer findAliveByRole(Role role) {
        return players.stream()
                .filter(WerewolfPlayer::isAlive)
                .filter(p -> p.getRole() == role)
                .findFirst().orElse(null);
    }

    /** 记录一条公开提示，只保留最近 10 条 */
    public void addMessage(String message) {
        messages.add(message);
        while (messages.size() > 10) {
            messages.remove(0);
        }
    }

    /** 全部狼的座位号（给狼人自己看队友用） */
    public List<Integer> wolfSeats() {
        return players.stream().filter(WerewolfPlayer::isWolf)
                .map(WerewolfPlayer::getSeatNo).collect(Collectors.toList());
    }

    // ==================== getter / setter ====================

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

    public GamePhase getPhase() {
        return phase;
    }

    public void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public List<WerewolfPlayer> getPlayers() {
        return players;
    }

    public Map<Integer, Integer> getWolfVotes() {
        return wolfVotes;
    }

    public Integer getWolfTarget() {
        return wolfTarget;
    }

    public void setWolfTarget(Integer wolfTarget) {
        this.wolfTarget = wolfTarget;
    }

    public Map<Integer, Boolean> getSeerResults() {
        return seerResults;
    }

    public Integer getSeerTarget() {
        return seerTarget;
    }

    public void setSeerTarget(Integer seerTarget) {
        this.seerTarget = seerTarget;
    }

    public boolean isSeerActed() {
        return seerActed;
    }

    public void setSeerActed(boolean seerActed) {
        this.seerActed = seerActed;
    }

    public boolean isWitchSaveUsed() {
        return witchSaveUsed;
    }

    public void setWitchSaveUsed(boolean witchSaveUsed) {
        this.witchSaveUsed = witchSaveUsed;
    }

    public boolean isWitchPoisonUsed() {
        return witchPoisonUsed;
    }

    public void setWitchPoisonUsed(boolean witchPoisonUsed) {
        this.witchPoisonUsed = witchPoisonUsed;
    }

    public boolean isWitchActed() {
        return witchActed;
    }

    public void setWitchActed(boolean witchActed) {
        this.witchActed = witchActed;
    }

    public Integer getWitchSavedSeat() {
        return witchSavedSeat;
    }

    public void setWitchSavedSeat(Integer witchSavedSeat) {
        this.witchSavedSeat = witchSavedSeat;
    }

    public Integer getWitchPoisonSeat() {
        return witchPoisonSeat;
    }

    public void setWitchPoisonSeat(Integer witchPoisonSeat) {
        this.witchPoisonSeat = witchPoisonSeat;
    }

    public List<Integer> getNightDeaths() {
        return nightDeaths;
    }

    public NightStep getNightStep() {
        return nightStep;
    }

    public void setNightStep(NightStep nightStep) {
        this.nightStep = nightStep;
    }

    public List<Integer> getLastNightDeaths() {
        return lastNightDeaths;
    }

    public List<Integer> getSpeakOrder() {
        return speakOrder;
    }

    public int getSpeakIndex() {
        return speakIndex;
    }

    public void setSpeakIndex(int speakIndex) {
        this.speakIndex = speakIndex;
    }

    public List<Map<String, Object>> getSpeeches() {
        return speeches;
    }

    public Map<Integer, Integer> getDayVotes() {
        return dayVotes;
    }

    public Set<Integer> getAbstainedSeats() {
        return abstainedSeats;
    }

    /** 已经表态的人数 = 投出去的票 + 弃权的 */
    public int votedOrAbstainedCount() {
        return dayVotes.size() + abstainedSeats.size();
    }

    public Integer getExiledSeat() {
        return exiledSeat;
    }

    public void setExiledSeat(Integer exiledSeat) {
        this.exiledSeat = exiledSeat;
    }

    public List<String> getMessages() {
        return messages;
    }

    public String getWinnerCamp() {
        return winnerCamp;
    }

    public void setWinnerCamp(String winnerCamp) {
        this.winnerCamp = winnerCamp;
    }

    public boolean isWolfDecided() {
        return wolfDecided;
    }

    public void setWolfDecided(boolean wolfDecided) {
        this.wolfDecided = wolfDecided;
    }

    public long getDeadline() {
        return deadline;
    }

    public void setDeadline(long deadline) {
        this.deadline = deadline;
    }
}
