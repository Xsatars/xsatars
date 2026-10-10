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

    /**
     * 状态版本号：每次状态推进（一次 apply 处理成功）就 +1，只增不减。
     *
     * <p>它会被装进 {@link com.aitable.game.core.PublicGameState#getStateSeq()} 发给前端。
     * 用途是解决<b>跨频道消息乱序</b>：前端同时从"房间频道"（公开事件）和
     * "用户频道"（全量状态）收状态，STOMP 不保证两者的到达顺序 ——
     * 旧快照可能后到并把新状态覆盖掉，玩家就会看到"白天闪一下狼人刀人"。
     * 有了这个号，前端就能识别并丢弃过期快照。</p>
     */
    private long stateSeq = 0;

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

    /**
     * 发言历史（<b>跨轮保留</b>）。
     *
     * <p>和上面那个 {@code speeches} 不一样：那个只看"这一轮谁发过言"，每轮会清空；
     * 这个是给 AI 当记忆用的，整局都留着 —— 这样它发言时才能提起
     * 「你昨天说我是狼」「3 号上一轮还在保 5 号」这种事。</p>
     */
    private final List<Map<String, Object>> speechHistory = new ArrayList<>();

    /**
     * 投票历史（<b>跨轮保留</b>）：每轮结算后记一条 {round, votes, exiledSeat}。
     *
     * <p>这是狼人杀里最有价值的信息之一：谁跟谁抱团、谁在关键时刻改票。</p>
     */
    private final List<Map<String, Object>> voteHistory = new ArrayList<>();

    /**
     * 这个房间里<b>之前几局</b>的战绩摘要（跨局记忆）。
     *
     * <p>和上面那些不一样：那些都是"本局内"的记忆，这个跨越了对局 ——
     * AI 能记得"上一局 3 号是狼，把我骗惨了"。真人玩家本来就会这么记，
     * 让 AI 也有这份印象，一桌人的"关系"才能延续下去。</p>
     */
    private final List<String> roomHistory = new ArrayList<>();

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

    /** 记录一条公开提示，只保留最近 60 条（够回溯到开局） */
    public void addMessage(String message) {
        messages.add(message);
        while (messages.size() > 60) {
            messages.remove(0);
        }
    }

    // ==================== AI 的记忆 ====================

    /** 记一条发言（跨轮保留，给 AI 当记忆） */
    public void recordSpeech(int round, int seatNo, String nickname, String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("round", round);
        item.put("seatNo", seatNo);
        item.put("nickname", nickname);
        item.put("text", text);
        speechHistory.add(item);
    }

    /** 记一轮投票（结算放逐结果时调用） */
    public void recordVote(int round, Map<Integer, Integer> votes, Integer exiledSeat) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("round", round);
        item.put("exiledSeat", exiledSeat);
        item.put("votes", new LinkedHashMap<>(votes));
        voteHistory.add(item);
    }

    /**
     * 把发言历史整理成给人读的文本（只取最近 limit 条）。
     */
    public List<String> speechHistoryText(int limit) {
        List<String> result = new ArrayList<>();
        int from = Math.max(0, speechHistory.size() - limit);
        for (int i = from; i < speechHistory.size(); i++) {
            Map<String, Object> item = speechHistory.get(i);
            result.add("第 " + item.get("round") + " 轮　" + item.get("seatNo") + " 号 "
                    + item.get("nickname") + "：" + item.get("text"));
        }
        return result;
    }

    /** 把投票历史整理成给人读的文本（只取最近 limit 轮） */
    public List<String> voteHistoryText(int limit) {        List<String> result = new ArrayList<>();
        int from = Math.max(0, voteHistory.size() - limit);
        for (int i = from; i < voteHistory.size(); i++) {
            Map<String, Object> item = voteHistory.get(i);
            Object raw = item.get("votes");
            StringBuilder sb = new StringBuilder();
            sb.append("第 ").append(item.get("round")).append(" 轮投票：");
            if (raw instanceof Map<?, ?> votes) {
                votes.forEach((voter, target) ->
                        sb.append(voter).append(" 号→").append(target).append(" 号　"));
            }
            Object exiled = item.get("exiledSeat");
            sb.append(exiled == null ? "（平票，没人出局）" : "（" + exiled + " 号被放逐）");
            result.add(sb.toString().trim());
        }
        return result;
    }

    /** 全部狼的座位号（给狼人自己看队友用） */
    public List<Integer> wolfSeats() {
        return players.stream().filter(WerewolfPlayer::isWolf)
                .map(WerewolfPlayer::getSeatNo).collect(Collectors.toList());
    }

    // ==================== 跨局记忆 ====================

    /** 房间之前几局的战绩摘要（开局时由外部装入） */
    public List<String> getRoomHistory() {
        return roomHistory;
    }

    public void setRoomHistory(List<String> history) {
        roomHistory.clear();
        if (history != null) {
            roomHistory.addAll(history);
        }
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

    public long getStateSeq() {
        return stateSeq;
    }

    public void setStateSeq(long stateSeq) {
        this.stateSeq = stateSeq;
    }

    /** 状态推进了一步：版本号 +1（由引擎在每次动作处理成功后调用） */
    public void bumpStateSeq() {
        this.stateSeq++;
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
