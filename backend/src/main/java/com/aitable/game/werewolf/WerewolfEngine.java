package com.aitable.game.werewolf;

import com.aitable.common.GameConstants;
import com.aitable.common.enums.Camp;
import com.aitable.common.enums.Role;
import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionResult;
import com.aitable.game.core.ActionType;
import com.aitable.game.core.GameEngine;
import com.aitable.game.core.GameEvent;
import com.aitable.game.core.GamePhase;
import com.aitable.game.core.GameView;
import com.aitable.game.core.PlayerPrivateState;
import com.aitable.game.core.PublicGameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 狼人杀引擎：<b>全部游戏规则都在这个类里</b>。
 *
 * <h3>这个类的三条铁律</h3>
 * <ol>
 *   <li><b>只有它能改游戏状态</b>。Controller、WebSocket、数据库都不许碰状态。</li>
 *   <li><b>先校验，后改状态</b>。行动不合法就返回失败，状态一点都不动。</li>
 *   <li><b>它是唯一知道所有人身份的地方</b>。对外只给
 *       {@link #viewFor(int)}（按座位过滤后的视图），完整状态永不外传。</li>
 * </ol>
 *
 * <h3>6 人局流程</h3>
 * <pre>
 * 分配角色（2 狼 + 1 预言家 + 1 女巫 + 2 平民）
 *   ↓
 * NIGHT        狼人刀人 → 预言家查验 → 女巫决定要不要用药
 *   ↓（三件都做完才结算）
 * DAY          公布昨晚死讯 → 判定胜负
 *   ↓
 * DAY_DISCUSSION 存活玩家依次发言
 *   ↓
 * DAY_VOTE     全体投票 → 票最多者出局（平票则无人出局）→ 判定胜负
 *   ↓
 * 没结束就回到 NIGHT（轮次 +1）；结束了进入 FINISHED
 * </pre>
 */
public class WerewolfEngine implements GameEngine {

    private static final Logger log = LoggerFactory.getLogger(WerewolfEngine.class);

    /** 单条发言的长度上限 */
    private static final int SPEECH_MAX_LENGTH = 200;

    /** 每步默认等待时间（秒） */
    public static final int DEFAULT_TIMEOUT_SECONDS = 90;

    /**
     * 最大轮数（兜底保护）。
     *
     * <p>如果所有人都挂机：夜晚空刀、白天没人投票 → 一直没人出局 → 理论上会永远循环，
     * 服务器就一直被这一局占着。所以到了这个轮数还没分出胜负，就判平局收场。</p>
     */
    public static final int MAX_ROUNDS = 20;

    /** 平局的标记（存进 games.winner_camp 和 game_records.result） */
    public static final String WINNER_DRAW = "DRAW";

    private final WerewolfState state;

    /** 洗牌用的随机源；测试时可以传固定种子，让角色分配可复现 */
    private final Random random;

    /** 每个需要玩家操作的步骤最多等多少毫秒；<=0 表示不启用超时 */
    private final long timeoutMillis;

    /**
     * 投票阶段的时限（毫秒）。
     *
     * <p>投票是"大家一起投"，20 秒足够；不像发言那样要一个人一个人等。</p>
     */
    private static final long VOTE_TIMEOUT_MILLIS = 20_000L;

    /** 用的板子 —— 决定这局几个人、有哪些角色 */
    private final Board board;

    /** 死因：被狼刀 */
    public static final String DEATH_WOLF = "WOLF";
    /** 死因：被女巫毒杀 —— 猎人因此开不了枪 */
    public static final String DEATH_POISON = "POISON";
    /** 死因：被投票放逐 */
    public static final String DEATH_VOTE = "VOTE";
    /** 死因：被猎人开枪带走 */
    public static final String DEATH_HUNTER = "HUNTER";

    public WerewolfEngine(WerewolfState state) {
        this(state, new Random(), DEFAULT_TIMEOUT_SECONDS, Board.DEFAULT);
    }

    public WerewolfEngine(WerewolfState state, Random random) {
        this(state, random, DEFAULT_TIMEOUT_SECONDS, Board.DEFAULT);
    }

    public WerewolfEngine(WerewolfState state, Random random, int timeoutSeconds) {
        this(state, random, timeoutSeconds, Board.DEFAULT);
    }

    public WerewolfEngine(WerewolfState state, Random random, int timeoutSeconds, Board board) {
        this.state = state;
        this.random = random;
        this.timeoutMillis = Math.max(timeoutSeconds, 0) * 1000L;
        this.board = board == null ? Board.DEFAULT : board;
    }

    /** 这局用的板子 */
    public Board getBoard() {
        return board;
    }

    /** 造一个引擎（开局前调用） */
    public static WerewolfEngine create(Long gameId, Long roomId, List<WerewolfPlayer> players) {
        return create(gameId, roomId, players, DEFAULT_TIMEOUT_SECONDS, Board.DEFAULT);
    }

    public static WerewolfEngine create(Long gameId, Long roomId, List<WerewolfPlayer> players, int timeoutSeconds) {
        return create(gameId, roomId, players, timeoutSeconds, Board.DEFAULT);
    }

    /** 造一个引擎，指定板子（8 人局走这个） */
    public static WerewolfEngine create(Long gameId, Long roomId, List<WerewolfPlayer> players,
                                        int timeoutSeconds, Board board) {
        Board actual = board == null ? Board.DEFAULT : board;
        WerewolfState state = new WerewolfState(gameId, roomId);
        state.getPlayers().addAll(players);
        state.getPlayers().sort((a, b) -> Integer.compare(a.getSeatNo(), b.getSeatNo()));
        return new WerewolfEngine(state, new Random(), timeoutSeconds, actual);
    }

    public WerewolfState getState() {
        return state;
    }

    /**
     * 装入"这个房间之前几局"的战绩摘要，作为 AI 的<b>跨局记忆</b>。
     *
     * <p>开局前调用一次。有了它，AI 才能说出"上一局 3 号就是狼，这次别信他"这种话 ——
     * 真人玩家本来就记得这些，AI 也该记得。只发给 AI，真人的 view 里没有。</p>
     */
    public void loadRoomHistory(java.util.List<String> history) {
        state.setRoomHistory(history);
    }

    @Override
    public String gameType() {
        return GameConstants.GAME_TYPE_WEREWOLF;
    }

    // ============================================================
    // 开局
    // ============================================================

    @Override
    public List<GameEvent> start() {
        assignRoles();

        state.setRound(1);
        state.setPhase(GamePhase.NIGHT);
        state.addMessage("游戏开始：第 1 夜，天黑请闭眼…");

        List<GameEvent> events = new ArrayList<>();
        events.add(GameEvent.publicEvent("GAME_START", publicState(), "游戏开始"));

        // 每个人的身份单独发给他自己 —— 这是最关键的隔离点
        for (WerewolfPlayer player : state.getPlayers()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("seatNo", player.getSeatNo());
            data.put("role", player.getRole().name());
            data.put("roleLabel", player.getRole().getLabel());
            data.put("camp", player.getRole().getCamp().name());
            if (player.isWolf()) {
                // 狼人额外拿到队友座位号
                data.put("teammateSeats", state.wolfSeats().stream()
                        .filter(seat -> seat != player.getSeatNo())
                        .collect(Collectors.toList()));
            }
            events.add(GameEvent.privateEvent("ROLE_ASSIGN", player.getSeatNo(), data,
                    "你的身份是「" + player.getRole().getLabel() + "」"));
        }

        events.add(GameEvent.publicEvent("PHASE_CHANGE", publicState(), "第 1 夜：天黑请闭眼"));
        // 开局就播报第一个角色的动作（"狼人请睁眼"）。
        // 不能指望 moveToNightStep 来报——nightStep 的初始值本来就是 WOLF，
        // 它那句"值没变就不播报"的判断会把这一步吞掉。
        String firstAnnounce = announceNightStep(state.getNightStep());
        state.addMessage(firstAnnounce);
        events.add(GameEvent.publicEvent("PHASE_CHANGE", publicState(), firstAnnounce));
        refreshDeadline();
        log.info("对局 {} 开始，共 {} 名玩家", state.getGameId(), state.getPlayers().size());
        return events;
    }

    /** 洗牌发身份 —— 发什么牌由当前板子决定（6 人局没有猎人，8 人局有） */
    private void assignRoles() {
        List<Role> deck = board.buildDeck(random);

        List<WerewolfPlayer> players = state.getPlayers();
        if (deck.size() != players.size()) {
            throw new IllegalStateException("板子【" + board.getLabel() + "】需要 " + deck.size()
                    + " 个人，实际来了 " + players.size() + " 个");
        }
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setRole(deck.get(i));
        }
    }

    // ============================================================
    // 行动入口
    // ============================================================

    @Override
    public ActionResult apply(ActionRequest request) {
        if (request == null || request.getType() == null) {
            return ActionResult.fail("无效的行动请求");
        }
        WerewolfPlayer player = state.findBySeat(request.getSeatNo());
        if (player == null) {
            return ActionResult.fail("你不在这一局里");
        }
        if (state.getPhase() == GamePhase.FINISHED) {
            return ActionResult.fail("游戏已经结束了");
        }

        ActionResult result = switch (request.getType()) {
            case WEREWOLF_KILL -> handleWolfKill(player, request.getTargetSeat());
            case SEER_CHECK -> handleSeerCheck(player, request.getTargetSeat());
            case WITCH_SAVE -> handleWitchSave(player);
            case WITCH_POISON -> handleWitchPoison(player, request.getTargetSeat());
            case SPEAK -> handleSpeak(player, request.getText());
            case VOTE -> handleVote(player, request.getTargetSeat());
            case HUNTER_SHOOT -> handleHunterShoot(player, request);
            case PASS -> handlePass(player);
        };

        // 有人在正常操作，就把超时时间往后推，免得"正打着突然被判超时"
        if (result.isSuccess()) {
            // ★ 状态版本号 +1。
            // 它会在这一刻之后生成的所有快照里（PHASE_CHANGE / GAME_STATE_UPDATE…）
            // 带上，前端据此丢弃"迟到"的旧快照 ——
            // 否则旧快照会覆盖新状态，出现"白天闪一下狼人刀人"这种鬼影。
            state.bumpStateSeq();
            refreshDeadline();
        }
        return result;
    }
    // ---------------- 夜晚 ----------------

    private ActionResult handleWolfKill(WerewolfPlayer player, Integer targetSeat) {
        ActionResult invalid = requireNightAction(player, Role.WOLF, "狼人");
        if (invalid != null) {
            return invalid;
        }
        if (targetSeat == null) {
            return ActionResult.fail("请选择要击杀的玩家");
        }
        WerewolfPlayer target = state.findBySeat(targetSeat);
        if (target == null || !target.isAlive()) {
            return ActionResult.fail("目标不合法");
        }
        if (target.isWolf()) {
            return ActionResult.fail("不能击杀自己的狼队友");
        }

        state.getWolfVotes().put(player.getSeatNo(), targetSeat);
        player.setActedTonight(true);

        List<GameEvent> events = new ArrayList<>();
        trySettleWolfTarget(events);
        advanceNightIfReady(events);
        return ActionResult.ok("已选择击杀 " + target.getNickname() + "（" + targetSeat + " 号）", events);
    }

    private ActionResult handleSeerCheck(WerewolfPlayer player, Integer targetSeat) {
        ActionResult invalid = requireNightAction(player, Role.SEER, "预言家");
        if (invalid != null) {
            return invalid;
        }
        WerewolfPlayer target = requireAliveTarget(targetSeat, "请选择一个存活的玩家");
        if (target == null) {
            return ActionResult.fail("请选择一个存活的玩家");
        }
        if (target.getSeatNo() == player.getSeatNo()) {
            return ActionResult.fail("不能查验自己");
        }

        boolean isWolf = target.isWolf();
        state.getSeerResults().put(target.getSeatNo(), isWolf);
        state.setSeerTarget(target.getSeatNo());
        state.setSeerActed(true);
        player.setActedTonight(true);

        List<GameEvent> events = new ArrayList<>();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetSeat", target.getSeatNo());
        data.put("targetNickname", target.getNickname());
        data.put("isWolf", isWolf);
        data.put("result", isWolf ? "狼人" : "好人");
        // 查验结果只有预言家本人收到
        events.add(GameEvent.privateEvent("NIGHT_RESULT", player.getSeatNo(), data,
                "查验结果：" + target.getSeatNo() + " 号 " + target.getNickname() + " 是「" + (isWolf ? "狼人" : "好人") + "」"));

        advanceNightIfReady(events);
        return ActionResult.ok("查验完成", events);
    }

    private ActionResult handleWitchSave(WerewolfPlayer player) {
        ActionResult invalid = requireWitchAction(player);
        if (invalid != null) {
            return invalid;
        }
        if (state.isWitchSaveUsed()) {
            return ActionResult.fail("解药已经用过了");
        }
        if (state.getWolfTarget() == null) {
            return ActionResult.fail("今晚还没有人被袭击，暂时不能用解药");
        }
        // 女巫可以自救：被刀的就是她自己时也能用解药。
        // （早期版本按"常见规则"禁止了自救，实测下来"女巫第一晚被刀就直接废掉"太伤，
        //   而且真人玩的时候也常允许自救，所以放开。）

        int saved = state.getWolfTarget();
        state.setWitchSavedSeat(saved);
        state.setWitchSaveUsed(true);
        state.setWitchActed(true);
        player.setActedTonight(true);

        List<GameEvent> events = new ArrayList<>();
        advanceNightIfReady(events);
        return ActionResult.ok("已使用解药救下 " + saved + " 号", events);
    }

    private ActionResult handleWitchPoison(WerewolfPlayer player, Integer targetSeat) {
        ActionResult invalid = requireWitchAction(player);
        if (invalid != null) {
            return invalid;
        }
        if (state.isWitchPoisonUsed()) {
            return ActionResult.fail("毒药已经用过了");
        }
        WerewolfPlayer target = requireAliveTarget(targetSeat, "请选择要毒杀的玩家");
        if (target == null) {
            return ActionResult.fail("请选择要毒杀的玩家");
        }
        if (target.getSeatNo() == player.getSeatNo()) {
            return ActionResult.fail("不能毒自己");
        }

        state.setWitchPoisonSeat(target.getSeatNo());
        state.setWitchPoisonUsed(true);
        state.setWitchActed(true);
        player.setActedTonight(true);

        List<GameEvent> events = new ArrayList<>();
        advanceNightIfReady(events);
        return ActionResult.ok("已对 " + targetSeat + " 号使用毒药", events);
    }

    /** 通用的"跳过"：女巫不用药 / 放弃发言 / 投票弃权 */
    private ActionResult handlePass(WerewolfPlayer player) {
        // ⓪ 猎人开枪环节：放弃开枪（他出局了，"过"就是不开枪）
        if (state.getPhase() == GamePhase.HUNTER_SHOOT) {
            return handleHunterShoot(player,
                    ActionRequest.of(player.getSeatNo(), ActionType.PASS, null));
        }

        // ① 夜晚：女巫今晚不用药
        if (state.getPhase() == GamePhase.NIGHT && player.getRole() == Role.WITCH) {
            ActionResult invalid = requireWitchAction(player);
            if (invalid != null) {
                return invalid;
            }
            state.setWitchActed(true);
            player.setActedTonight(true);

            List<GameEvent> events = new ArrayList<>();
            advanceNightIfReady(events);
            return ActionResult.ok("今晚不使用药水", events);
        }

        // ② 发言阶段：当前发言者放弃发言
        if (state.getPhase() == GamePhase.DAY_DISCUSSION
                && WerewolfViews.currentSpeakerSeat(state) == player.getSeatNo()) {
            List<GameEvent> events = new ArrayList<>();
            skipCurrentSpeaker(events, "放弃发言");
            if (state.getSpeakIndex() >= state.getSpeakOrder().size()) {
                startVote(events);
            }
            return ActionResult.ok("已放弃发言", events);
        }

        // ③ 投票阶段：弃权。不计票，但算"已表态"，否则永远等不到"所有人都投完"
        if (state.getPhase() == GamePhase.DAY_VOTE && player.isAlive()
                && !state.getDayVotes().containsKey(player.getSeatNo())
                && !state.getAbstainedSeats().contains(player.getSeatNo())) {
            state.getAbstainedSeats().add(player.getSeatNo());
            state.addMessage(player.getNickname() + " 选择弃权");

            List<GameEvent> events = new ArrayList<>();
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("voterSeat", player.getSeatNo());
            update.put("voterNickname", player.getNickname());
            update.put("abstained", true);
            update.put("votedCount", state.votedOrAbstainedCount());
            update.put("totalVoters", state.alivePlayers().size());
            events.add(GameEvent.publicEvent("VOTE_UPDATE", update, player.getNickname() + " 弃权"));

            if (state.votedOrAbstainedCount() >= state.alivePlayers().size()) {
                settleVote(events);
            }
            return ActionResult.ok("已弃权", events);
        }

        return ActionResult.fail("现在没有可以放弃的操作");
    }

    // ---------------- 白天 ----------------

    private ActionResult handleSpeak(WerewolfPlayer player, String text) {
        if (state.getPhase() != GamePhase.DAY_DISCUSSION) {
            return ActionResult.fail("现在不是发言阶段");
        }
        int current = WerewolfViews.currentSpeakerSeat(state);
        if (current != player.getSeatNo()) {
            return ActionResult.fail("还没轮到你发言");
        }
        // 注意：这里故意不检查存活 —— 昨夜出局的人要先说遗言
        if (!StringUtils.hasText(text)) {
            return ActionResult.fail("发言内容不能为空");
        }
        String trimmed = text.trim();
        if (trimmed.length() > SPEECH_MAX_LENGTH) {
            return ActionResult.fail("发言太长了（最多 " + SPEECH_MAX_LENGTH + " 字）");
        }

        Map<String, Object> speech = new LinkedHashMap<>();
        speech.put("seatNo", player.getSeatNo());
        speech.put("nickname", player.getNickname());
        speech.put("text", trimmed);
        state.getSpeeches().add(speech);
        // 同时记进"跨轮保留的历史" —— AI 靠它形成记忆，发言时能引用前面几轮的话
        state.recordSpeech(state.getRound(), player.getSeatNo(), player.getNickname(), trimmed);
        player.setSpoke(true);
        state.setSpeakIndex(state.getSpeakIndex() + 1);

        List<GameEvent> events = new ArrayList<>();
        events.add(GameEvent.publicEvent("PLAYER_SPEAK", speech, player.getNickname() + " 发言"));

        if (state.getSpeakIndex() >= state.getSpeakOrder().size()) {
            startVote(events);
        }
        return ActionResult.ok("发言成功", events);
    }

    private ActionResult handleVote(WerewolfPlayer player, Integer targetSeat) {
        if (state.getPhase() != GamePhase.DAY_VOTE) {
            return ActionResult.fail("现在不是投票阶段");
        }
        if (!player.isAlive()) {
            return ActionResult.fail("你已经出局了，不能投票");
        }
        if (state.getDayVotes().containsKey(player.getSeatNo())) {
            return ActionResult.fail("你已经投过票了");
        }
        WerewolfPlayer target = requireAliveTarget(targetSeat, "请选择要放逐的玩家");
        if (target == null) {
            return ActionResult.fail("请选择要放逐的玩家");
        }
        if (target.getSeatNo() == player.getSeatNo()) {
            return ActionResult.fail("不能投票给自己");
        }

        state.getDayVotes().put(player.getSeatNo(), targetSeat);

        List<GameEvent> events = new ArrayList<>();
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("voterSeat", player.getSeatNo());
        update.put("voterNickname", player.getNickname());
        update.put("targetSeat", targetSeat);
        update.put("votedCount", state.getDayVotes().size());
        update.put("totalVoters", state.alivePlayers().size());
        events.add(GameEvent.publicEvent("VOTE_UPDATE", update,
                player.getNickname() + " 投给了 " + targetSeat + " 号"));

        if (state.votedOrAbstainedCount() >= state.alivePlayers().size()) {
            settleVote(events);
        }
        return ActionResult.ok("投票成功", events);
    }

    // ============================================================
    // 流程推进
    // ============================================================

    /** 所有活着的狼都投完之后，定下今晚的刀口（平票取座位号小的那个） */
    private void trySettleWolfTarget(List<GameEvent> events) {
        if (state.isWolfDecided()) {
            return;
        }
        List<WerewolfPlayer> wolves = state.aliveWolves();
        if (wolves.isEmpty()) {
            state.setWolfDecided(true);
            return;
        }
        if (state.getWolfVotes().size() < wolves.size()) {
            return; // 还有狼没选
        }
        decideWolfTarget(events);
    }

    /** 按目前已投的票定下刀口；一张票都没有就是"空刀"（超时会走到这里） */
    private void decideWolfTarget(List<GameEvent> events) {
        state.setWolfDecided(true);

        if (state.getWolfVotes().isEmpty()) {
            state.setWolfTarget(null);
            return;
        }

        Map<Integer, Long> counts = state.getWolfVotes().values().stream()
                .collect(Collectors.groupingBy(target -> target, Collectors.counting()));
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<Integer> top = counts.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());

        if (top.size() > 1) {
            // 狼人意见不统一（票数一样多）→ 今晚不刀人
            state.setWolfTarget(null);
            // 用一个**独立**的事件类型：以前这里复用了 NIGHT_RESULT，
            // 结果主持人（和别的按类型分发的逻辑）会把它误当成"天亮结算"，
            // 于是明明死了人却说成平安夜。语义不同就该用不同的类型。
            events.add(GameEvent.publicEvent("WOLF_NO_KILL",
                    Map.of("wolfNoKill", true, "reason", "SPLIT"),
                    "狼人意见不统一，今晚没有刀人"));
            return;
        }

        int chosen = top.get(0);
        state.setWolfTarget(chosen);

        // 通知女巫"今晚谁被袭击了" —— 她需要这个信息才能决定要不要用解药
        WerewolfPlayer witch = state.findAliveByRole(Role.WITCH);
        if (witch != null && !state.isWitchActed()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("wolfTargetSeat", chosen);
            data.put("wolfTargetNickname", state.findBySeat(chosen).getNickname());
            events.add(GameEvent.privateEvent("NIGHT_RESULT", witch.getSeatNo(), data,
                    "今晚 " + chosen + " 号被狼人袭击了"));
        }
    }

    /**
     * 推进夜晚：按「狼人 → 女巫 → 预言家」的顺序，谁该行动就把这一步交给他；
     * 三步都完成了才结算夜晚。
     *
     * <p>顺序不能乱：女巫要先知道「今晚谁被刀」才能决定救不救，所以必须排在狼人之后。</p>
     */
    private void advanceNightIfReady(List<GameEvent> events) {
        if (state.getPhase() != GamePhase.NIGHT) {
            return;
        }

        // ① 狼人：都决定了才轮到下一步
        if (!state.aliveWolves().isEmpty() && !state.isWolfDecided()) {
            moveToNightStep(NightStep.WOLF, events);
            return;
        }
        // ② 女巫
        if (state.findAliveByRole(Role.WITCH) != null && !state.isWitchActed()) {
            moveToNightStep(NightStep.WITCH, events);
            return;
        }
        // ③ 预言家
        if (state.findAliveByRole(Role.SEER) != null && !state.isSeerActed()) {
            moveToNightStep(NightStep.SEER, events);
            return;
        }
        settleNight(events);
    }

    /**
     * 换到夜晚的下一步，并重置超时时间。
     *
     * <p>会公开发一句"狼人请睁眼…"—— 这跟真人玩的时候主持人喊话一个道理：
     * 大家知道现在轮到哪个角色了，但<b>不会</b>泄露他选了谁。
     * 有了这句播报，围观的人和出局的人才知道场上进行到哪一步，不至于一片空白。</p>
     */
    private void moveToNightStep(NightStep step, List<GameEvent> events) {
        if (state.getNightStep() != step) {
            state.setNightStep(step);
            refreshDeadline();
            String announce = announceNightStep(step);
            // 两件事都要做：进游戏日志（所有人翻记录能看到）+ 广播出去（前端实时显示）
            state.addMessage(announce);
            events.add(GameEvent.publicEvent("PHASE_CHANGE", publicState(), announce));
        }
    }

    /** 夜晚每一步的公开播报（只报"轮到谁"，不含任何机密目标） */
    private String announceNightStep(NightStep step) {
        return switch (step) {
            case WOLF -> "狼人请睁眼，狼队请选择今晚要击杀的人";
            case WITCH -> "女巫请睁眼，你有一瓶解药和一瓶毒药";
            case SEER -> "预言家请睁眼，请选择你要查验的人";
        };
    }

    // ============================================================
    // 猎人开枪（8 人局）
    // ============================================================

    /** 等着开枪的猎人座位；null 表示没有待处理的 */
    private Integer pendingHunterSeat;

    /** 猎人是在哪一步出局的：NIGHT / VOTE —— 开完枪要回到对应的流程继续走 */
    private String hunterDeathSource;

    /** 还有猎人在等着开枪吗 */
    public boolean isWaitingForHunter() {
        return pendingHunterSeat != null;
    }

    public Integer getPendingHunterSeat() {
        return pendingHunterSeat;
    }

    /**
     * 死者里有猎人吗？有就开一个"开枪"环节，整个流程在这里<b>暂停</b>。
     *
     * <p>标准规则：被狼刀死、被投票放逐的猎人都能开枪；<b>被女巫毒死的不能</b> ——
     * 毒药会让技能失效，这也是狼队反制猎人的办法（前提是他们猜得到谁是猎人）。</p>
     *
     * @return true = 已经停下来等开枪了，调用方要立刻 return，别再往下走
     */
    private boolean openHunterShoot(List<Integer> deaths, String source, List<GameEvent> events) {
        for (Integer seat : deaths) {
            WerewolfPlayer dead = state.findBySeat(seat);
            if (dead == null || dead.getRole() != Role.HUNTER) {
                continue;
            }
            if (DEATH_POISON.equals(dead.getDeathReason())) {
                String msg = dead.getNickname() + "（" + seat + " 号）被毒杀，猎人来不及开枪";
                state.addMessage(msg);
                events.add(GameEvent.publicEvent("HUNTER_CANNOT_SHOOT",
                        Map.of("seatNo", seat), msg));
                continue;
            }
            pendingHunterSeat = seat;
            hunterDeathSource = source;
            state.setPhase(GamePhase.HUNTER_SHOOT);
            refreshDeadline();
            String text = "🔫 " + dead.getNickname() + "（" + seat + " 号）是猎人，可以开枪带走一个人";
            state.addMessage(text);
            events.add(GameEvent.publicEvent("HUNTER_SHOOT",
                    Map.of("seatNo", seat, "nickname", dead.getNickname()), text));
            return true;
        }
        return false;
    }

    /** 猎人开枪（或者放弃） */
    private ActionResult handleHunterShoot(WerewolfPlayer player, ActionRequest request) {
        if (pendingHunterSeat == null || player.getSeatNo() != pendingHunterSeat) {
            return ActionResult.fail("现在不是你开枪");
        }

        List<GameEvent> events = new ArrayList<>();
        boolean giveUp = request.getType() == ActionType.PASS || request.getTargetSeat() == null;

        if (!giveUp) {
            WerewolfPlayer target = state.findBySeat(request.getTargetSeat());
            if (target == null) {
                return ActionResult.fail("查无此人");
            }
            if (!target.isAlive()) {
                return ActionResult.fail("不能对已经出局的人开枪");
            }
            if (target.getSeatNo() == player.getSeatNo()) {
                return ActionResult.fail("不能对自己开枪");
            }
            target.die(DEATH_HUNTER);
            String text = "🔫 " + player.getNickname() + "（" + player.getSeatNo()
                    + " 号）开枪带走了 " + target.getNickname() + "（" + target.getSeatNo() + " 号）";
            state.addMessage(text);
            events.add(GameEvent.publicEvent("HUNTER_SHOOT_RESULT",
                    Map.of("hunterSeat", player.getSeatNo(),
                            "targetSeat", target.getSeatNo(),
                            "targetNickname", target.getNickname()), text));

            Map<String, Object> deadData = new LinkedHashMap<>();
            deadData.put("seatNo", target.getSeatNo());
            deadData.put("nickname", target.getNickname());
            deadData.put("reason", "HUNTER");
            events.add(GameEvent.publicEvent("PLAYER_DEAD", deadData,
                    target.getNickname() + "（" + target.getSeatNo() + " 号）出局了"));
        } else {
            String text = "🔫 " + player.getNickname() + "（" + player.getSeatNo() + " 号）选择不开枪";
            state.addMessage(text);
            events.add(GameEvent.publicEvent("HUNTER_SHOOT_RESULT",
                    Map.of("hunterSeat", player.getSeatNo(), "targetSeat", 0), text));
        }

        // 收尾：清掉待处理状态，回到他出局前的那条流程上继续
        String source = hunterDeathSource;
        pendingHunterSeat = null;
        hunterDeathSource = null;
        resumeAfterHunter(source, events);
        return ActionResult.ok("", events);
    }

    /** 猎人开完枪了，接着往下走 */
    private void resumeAfterHunter(String source, List<GameEvent> events) {
        if ("VOTE".equals(source)) {
            // 投票环节：接着做原来放逐之后该做的事
            afterVoteExile(events);
        } else {
            // 夜晚环节：该进白天了
            enterDayAfterNight(events);
        }
    }

    /** 结算夜晚：算出谁死，然后进入白天 */
    private void settleNight(List<GameEvent> events) {
        List<Integer> deaths = new ArrayList<>();
        if (state.getWolfTarget() != null && !state.getWolfTarget().equals(state.getWitchSavedSeat())) {
            deaths.add(state.getWolfTarget());
        }
        if (state.getWitchPoisonSeat() != null) {
            deaths.add(state.getWitchPoisonSeat());
        }
        deaths = deaths.stream().distinct().collect(Collectors.toList());

        state.getNightDeaths().clear();
        state.getNightDeaths().addAll(deaths);
        // 记下昨夜死的人，白天要让他们先说遗言（用完就清）
        state.getLastNightDeaths().clear();
        state.getLastNightDeaths().addAll(deaths);
        for (Integer seat : deaths) {
            WerewolfPlayer dead = state.findBySeat(seat);
            if (dead != null) {
                // 死因要分清：被毒死和被刀死对猎人来说是两回事（毒死不能开枪）
                dead.die(seat.equals(state.getWitchPoisonSeat()) ? DEATH_POISON : DEATH_WOLF);
            }
        }

        String text = deaths.isEmpty()
                ? "第 " + state.getRound() + " 天：昨晚是平安夜，没有人倒下"
                : "第 " + state.getRound() + " 天：昨晚倒牌的是 " + deaths.stream()
                        .map(seat -> seat + " 号").collect(Collectors.joining("、"));
        state.addMessage(text);

        Map<String, Object> nightResult = new LinkedHashMap<>();
        nightResult.put("round", state.getRound());
        nightResult.put("deaths", deaths);
        nightResult.put("safeNight", deaths.isEmpty());
        events.add(GameEvent.publicEvent("NIGHT_RESULT", nightResult, text));

        for (Integer seat : deaths) {
            WerewolfPlayer dead = state.findBySeat(seat);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("seatNo", seat);
            data.put("nickname", dead.getNickname());
            data.put("reason", "NIGHT");
            events.add(GameEvent.publicEvent("PLAYER_DEAD", data,
                    dead.getNickname() + "（" + seat + " 号）出局了"));
        }

        // ★ 昨晚倒牌的是猎人？先把枪开完，再进白天公布后面的流程
        if (openHunterShoot(deaths, "NIGHT", events)) {
            return;
        }

        enterDayAfterNight(events);
    }

    /**
     * 夜晚的死亡结算完成之后要做的事。
     *
     * <p>单独抽出来是因为猎人开枪会把流程岔开：他开完枪还要回到这里，
     * 把"公布死讯 → 判胜负 → 进入发言"接着走完。</p>
     */
    private void enterDayAfterNight(List<GameEvent> events) {
        // 先进入"公布死讯"这一刻
        state.setPhase(GamePhase.DAY);
        events.add(GameEvent.publicEvent("PHASE_CHANGE", publicState(),
                "第 " + state.getRound() + " 天：公布死讯"));

        if (checkWinCondition()) {
            endGame(events);
            return;
        }

        startDiscussion(events);
    }

    /** 进入发言阶段：存活玩家按座位号依次发言 */
    private void startDiscussion(List<GameEvent> events) {
        state.setPhase(GamePhase.DAY_DISCUSSION);
        state.getSpeakOrder().clear();
        state.getSpeeches().clear();
        state.setSpeakIndex(0);
        for (WerewolfPlayer player : state.getPlayers()) {
            player.setSpoke(false);
        }

        // 先让昨夜出局的人说遗言，再轮到存活玩家
        List<Integer> lastWords = new ArrayList<>(state.getLastNightDeaths());
        lastWords.sort(Integer::compareTo);
        state.getSpeakOrder().addAll(lastWords);
        for (WerewolfPlayer player : state.alivePlayers()) {
            state.getSpeakOrder().add(player.getSeatNo());
        }
        state.getLastNightDeaths().clear();

        if (state.getSpeakOrder().isEmpty()) {
            startVote(events);
            return;
        }

        String opening = lastWords.isEmpty()
                ? "请存活玩家依次发言"
                : "昨夜出局的 " + lastWords.stream().map(seat -> seat + " 号").collect(Collectors.joining("、"))
                    + " 先说遗言";
        state.addMessage(opening);
        events.add(GameEvent.publicEvent("PHASE_CHANGE", publicState(),
                opening + "，从 " + state.getSpeakOrder().get(0) + " 号开始"));
    }

    /** 进入投票阶段 */
    private void startVote(List<GameEvent> events) {
        state.setPhase(GamePhase.DAY_VOTE);
        state.getDayVotes().clear();
        state.getAbstainedSeats().clear();
        // 先清掉上一阶段留下的期限，下面 refreshDeadline 才知道"这是刚进入投票"，
        // 从而设上 20 秒。之后无论谁投票，这个期限都不会再变。
        state.setDeadline(0);
        // ★ 投票是"大家一起投"，整个阶段共用一个 20 秒的期限：
        //   这里设一次就不动了 —— 谁投票都不会把时间往后推，20 秒到点就结算。
        //   （之前这里忘了设，投票阶段一直在用发言阶段留下的 90 秒。）
        refreshDeadline();
        state.addMessage("发言结束，开始投票（20 秒内投完，没投的算弃权）");
        events.add(GameEvent.publicEvent("PHASE_CHANGE", publicState(),
                "开始投票：请在 20 秒内选出你想放逐的人，没投票的视为弃权"));
    }

    /** 结算投票：票最多的人出局；平票则本轮无人出局 */
    private void settleVote(List<GameEvent> events) {
        Map<Integer, Integer> counts = WerewolfViews.voteCounts(state);
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Integer> top = counts.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("voteCounts", counts);
        result.put("topSeats", top);
        result.put("tie", top.size() > 1);

        if (top.isEmpty()) {
            // 一个人都没投票（全都超时弃权了）
            state.setExiledSeat(null);
            result.put("exiledSeat", null);
            state.addMessage("没有人投票，本轮无人出局");
            events.add(GameEvent.publicEvent("VOTE_RESULT", result, "没有人投票，本轮无人被放逐"));
        } else if (top.size() > 1) {
            state.setExiledSeat(null);
            result.put("exiledSeat", null);
            state.addMessage("平票，本轮无人出局");
            events.add(GameEvent.publicEvent("VOTE_RESULT", result, "平票，本轮无人被放逐"));
        } else {
            int exiled = top.get(0);
            WerewolfPlayer target = state.findBySeat(exiled);
            target.die(DEATH_VOTE);
            state.setExiledSeat(exiled);
            result.put("exiledSeat", exiled);
            result.put("exiledNickname", target.getNickname());
            state.addMessage(exiled + " 号 " + target.getNickname() + " 被投票放逐");

            events.add(GameEvent.publicEvent("VOTE_RESULT", result,
                    target.getNickname() + "（" + exiled + " 号）被投票放逐"));

            Map<String, Object> deadData = new LinkedHashMap<>();
            deadData.put("seatNo", exiled);
            deadData.put("nickname", target.getNickname());
            deadData.put("reason", "VOTE");
            events.add(GameEvent.publicEvent("PLAYER_DEAD", deadData, target.getNickname() + " 出局了"));

            // ★ 被票出去的是猎人？先让他开枪
            if (openHunterShoot(List.of(exiled), "VOTE", events)) {
                return;
            }
        }

        afterVoteExile(events);
    }

    /**
     * 投票放逐结算完之后要做的事。
     *
     * <p>同样单独抽出来：猎人开枪会把流程岔开，他开完枪还要回到这里，
     * 把"记投票历史 → 判胜负 → 进入下一夜"接着走完。</p>
     */
    private void afterVoteExile(List<GameEvent> events) {
        // 把这一轮的投票明细记进历史 —— 这是 AI 判断"谁跟谁抱团、谁临阵改票"的关键材料
        state.recordVote(state.getRound(), state.getDayVotes(), state.getExiledSeat());

        if (checkWinCondition()) {
            endGame(events);
            return;
        }

        // 兜底：一直没人出局（例如全员挂机）时，不能让对局无限循环下去
        if (state.getRound() >= MAX_ROUNDS) {
            state.setWinnerCamp(WINNER_DRAW);
            endGame(events);
            return;
        }

        // 进入下一夜
        state.setRound(state.getRound() + 1);
        startNight(events);
    }

    /** 进入夜晚：清掉上一夜的行动记录 */
    private void startNight(List<GameEvent> events) {
        state.setPhase(GamePhase.NIGHT);
        state.getWolfVotes().clear();
        state.setWolfTarget(null);
        state.setWolfDecided(false);
        state.setSeerTarget(null);
        state.setSeerActed(false);
        state.setWitchActed(false);
        state.setWitchSavedSeat(null);
        state.setWitchPoisonSeat(null);
        state.getNightDeaths().clear();
        // 投票数据也要清空，否则会残留到下一轮的界面里（第二天还显示昨天的票数）
        state.getDayVotes().clear();
        state.getAbstainedSeats().clear();
        state.setExiledSeat(null);
        for (WerewolfPlayer player : state.getPlayers()) {
            player.setActedTonight(false);
        }
        // 夜晚从"狼人行动"开始
        state.setNightStep(NightStep.WOLF);
        refreshDeadline();

        state.addMessage("第 " + state.getRound() + " 夜：天黑请闭眼…");
        events.add(GameEvent.publicEvent("PHASE_CHANGE", publicState(),
                "第 " + state.getRound() + " 夜：天黑请闭眼"));
    }

    // ============================================================
    // 超时处理
    // ============================================================

    /** 当前这一步是不是已经等超时了 */
    public boolean isTimeout() {
        return state.getDeadline() > 0 && System.currentTimeMillis() > state.getDeadline();
    }

    /**
     * 超时了：替还没操作的人做默认决定，让对局继续往下走。
     *
     * <ul>
     *   <li><b>夜晚</b>：没选目标的狼算弃权（一张票都没有就是"空刀"）；预言家跳过；
     *       女巫视为不用药</li>
     *   <li><b>发言</b>：当前发言者记为"（超时未发言）"，轮到下一个人</li>
     *   <li><b>投票</b>：没投的人弃权，直接结算</li>
     * </ul>
     */
    public ActionResult timeout() {
        if (state.getPhase() == GamePhase.FINISHED) {
            return ActionResult.fail("游戏已经结束了");
        }

        List<GameEvent> events = new ArrayList<>();
        switch (state.getPhase()) {
            case NIGHT -> {
                // 只处理"当前这一步"该行动的人，然后继续推进
                NightStep step = state.getNightStep();
                if (step == NightStep.WOLF) {
                    state.addMessage("夜晚行动超时，自动跳过");
                    for (WerewolfPlayer wolf : state.aliveWolves()) {
                        wolf.setActedTonight(true);
                    }
                    decideWolfTarget(events);
                    if (state.getWolfTarget() == null) {
                        events.add(GameEvent.publicEvent("NIGHT_RESULT",
                                Map.of("wolfNoKill", true), "狼人没能选出目标，今晚空刀"));
                    }
                } else if (step == NightStep.WITCH) {
                    WerewolfPlayer witch = state.findAliveByRole(Role.WITCH);
                    if (witch != null && !state.isWitchActed()) {
                        state.setWitchActed(true);
                        events.add(GameEvent.privateEvent("NIGHT_RESULT", witch.getSeatNo(),
                                Map.of("skipped", true), "你超时未做决定，视为不用药"));
                    }
                } else {
                    WerewolfPlayer seer = state.findAliveByRole(Role.SEER);
                    if (seer != null && !state.isSeerActed()) {
                        state.setSeerActed(true);
                        events.add(GameEvent.privateEvent("NIGHT_RESULT", seer.getSeatNo(),
                                Map.of("skipped", true), "你超时未查验，本晚跳过"));
                    }
                }
                advanceNightIfReady(events);
            }
            case DAY_DISCUSSION -> {
                skipCurrentSpeaker(events, "超时未发言");
                if (state.getSpeakIndex() >= state.getSpeakOrder().size()) {
                    startVote(events);
                }
            }
            case DAY_VOTE -> {
                // 没投的人都算弃权，否则永远等不到"所有人都表态"
                for (WerewolfPlayer player : state.alivePlayers()) {
                    if (!state.getDayVotes().containsKey(player.getSeatNo())) {
                        state.getAbstainedSeats().add(player.getSeatNo());
                    }
                }
                state.addMessage("投票超时，未投票的玩家视为弃权");
                settleVote(events);
            }
            case HUNTER_SHOOT -> {
                // 猎人超时不开枪 = 放弃开枪。
                // 不能让全桌一直等一个已经出局的人 —— 他只是少带走一个人而已。
                if (pendingHunterSeat != null) {
                    state.addMessage("🔫 猎人开枪超时，视为放弃");
                    String source = hunterDeathSource;
                    pendingHunterSeat = null;
                    hunterDeathSource = null;
                    resumeAfterHunter(source, events);
                }
            }
            default -> {
                return ActionResult.fail("当前阶段不需要玩家操作");
            }
        }

        // 超时同样改了状态，同样会产出快照 —— 版本号也要 +1。
        // 不然这些快照的版本号"原地踏步"，前端可能把它当成重复消息丢掉，
        // 那样超时推进在界面上就看不到效果了。
        state.bumpStateSeq();
        refreshDeadline();
        return ActionResult.ok("超时已自动处理", events);
    }

    /**
     * 某个玩家弃权（通常是掉线了）：按他的角色和当前阶段做默认决定。
     *
     * <p>为什么不等超时：掉线的人显然不会再操作了，让他一个人拖住全桌 90 秒没有意义。</p>
     */
    public ActionResult forfeit(int seatNo) {
        WerewolfPlayer player = state.findBySeat(seatNo);
        if (player == null) {
            return ActionResult.fail("该玩家不在局中");
        }
        if (state.getPhase() == GamePhase.FINISHED) {
            return ActionResult.fail("游戏已经结束");
        }
        if (!player.isAlive()) {
            // 出局的人只有在"说遗言"时才需要处理
            if (state.getPhase() == GamePhase.DAY_DISCUSSION
                    && WerewolfViews.currentSpeakerSeat(state) == seatNo) {
                List<GameEvent> events = new ArrayList<>();
                skipCurrentSpeaker(events, "掉线跳过");
                if (state.getSpeakIndex() >= state.getSpeakOrder().size()) {
                    startVote(events);
                }
                return ActionResult.ok(player.getNickname() + " 的遗言已跳过", events);
            }
            return ActionResult.fail("该玩家已出局，当前不需要操作");
        }

        List<GameEvent> events = new ArrayList<>();
        boolean didSomething = false;

        switch (state.getPhase()) {
            case NIGHT -> {
                NightStep step = state.getNightStep();
                if (step == NightStep.WOLF && player.isWolf() && !player.isActedTonight()) {
                    player.setActedTonight(true);
                    // 所有活狼都"投了或掉线"之后就可以定刀口（按已投的票，可能空刀）
                    boolean allWolvesDone = state.aliveWolves().stream()
                            .allMatch(WerewolfPlayer::isActedTonight);
                    if (allWolvesDone) {
                        decideWolfTarget(events);
                    }
                    didSomething = true;
                    state.addMessage(player.getNickname() + " 掉线，自动跳过");
                } else if (step == NightStep.WITCH && player.getRole() == Role.WITCH
                        && !state.isWitchActed()) {
                    state.setWitchActed(true);
                    didSomething = true;
                    state.addMessage(player.getNickname() + " 掉线，视为不用药");
                } else if (step == NightStep.SEER && player.getRole() == Role.SEER
                        && !state.isSeerActed()) {
                    state.setSeerActed(true);
                    didSomething = true;
                    state.addMessage(player.getNickname() + " 掉线，跳过查验");
                }
                advanceNightIfReady(events);
            }
            case DAY_DISCUSSION -> {
                if (WerewolfViews.currentSpeakerSeat(state) == seatNo) {
                    skipCurrentSpeaker(events, "掉线跳过");
                    didSomething = true;
                    if (state.getSpeakIndex() >= state.getSpeakOrder().size()) {
                        startVote(events);
                    }
                }
            }
            case DAY_VOTE -> {
                if (!state.getDayVotes().containsKey(seatNo)
                        && !state.getAbstainedSeats().contains(seatNo)) {
                    state.getAbstainedSeats().add(seatNo);
                    didSomething = true;
                    state.addMessage(player.getNickname() + " 掉线，本轮弃权");
                    if (state.votedOrAbstainedCount() >= state.alivePlayers().size()) {
                        settleVote(events);
                    }
                }
            }
            default -> {
                return ActionResult.fail("当前阶段不需要玩家操作");
            }
        }

        if (!didSomething) {
            return ActionResult.fail("当前不需要该玩家操作");
        }
        return ActionResult.ok(player.getNickname() + " 已自动跳过", events);
    }

    /** 让当前发言者跳过（超时或掉线时用） */
    private void skipCurrentSpeaker(List<GameEvent> events, String reason) {
        int speaker = WerewolfViews.currentSpeakerSeat(state);
        if (speaker <= 0) {
            return;
        }
        WerewolfPlayer player = state.findBySeat(speaker);
        Map<String, Object> speech = new LinkedHashMap<>();
        speech.put("seatNo", speaker);
        speech.put("nickname", player.getNickname());
        speech.put("text", "（" + reason + "）");
        speech.put("timeout", true);
        state.getSpeeches().add(speech);
        player.setSpoke(true);
        state.setSpeakIndex(state.getSpeakIndex() + 1);
        events.add(GameEvent.publicEvent("PLAYER_SPEAK", speech, player.getNickname() + " " + reason));
    }

    /**
     * 把超时时间往后推（有人操作、或阶段刚变化时调用）。
     *
     * <p>投票阶段用的是<b>另一套时限</b>：投票是"大家同时投"的一件事，
     * 20 秒足够每个人点一下，拖太久只会让全桌干等。
     * 而且这个期限<b>只在进入投票时设一次</b> —— 谁投票都不会让它顺延。</p>
     */
    private void refreshDeadline() {
        if (timeoutMillis <= 0
                || state.getPhase() == GamePhase.FINISHED
                || state.getPhase() == GamePhase.WAITING) {
            state.setDeadline(0);
            return;
        }
        // 投票阶段：进入那一刻设一次 20 秒，之后**谁都不许改**。
        // 因为投票是"大家一起投"，每有人投一票就顺延的话，等于没有时限。
        if (state.getPhase() == GamePhase.DAY_VOTE) {
            if (state.getDeadline() <= 0) {
                state.setDeadline(System.currentTimeMillis() + VOTE_TIMEOUT_MILLIS);
            }
            return;
        }
        state.setDeadline(System.currentTimeMillis() + timeoutMillis);
    }

    // ============================================================
    // 胜负判定
    // ============================================================

    /**
     * 立刻做一次胜负判定（包级可见，主要给测试用）。
     *
     * <p>正常流程里判定是挂在"死亡结算之后"自动跑的，不用手动调。
     * 但测试常常直接把人的 alive 置 false 来造局面 —— 那样绕过了结算点，
     * 需要一个口子把判定补上。</p>
     */
    boolean evaluateWinConditionNow() {
        return checkWinCondition();
    }

    /**
     * 判定胜负，返回是否已结束。
     *
     * <p><b>屠边</b>（线下最常用的规则）：</p>
     * <ul>
     *   <li>狼人全部出局 → 好人胜</li>
     *   <li><b>所有平民死光</b> → 狼人胜（屠民）</li>
     *   <li><b>所有神职死光</b> → 狼人胜（屠神）</li>
     * </ul>
     *
     * <p>为什么换掉原来那套"狼数 ≥ 好人数"：那样 6 人局要杀到只剩 2 个好人、
     * 8 人局更久，一局又长又黏；而且狼队没有明确的战术目标，只能硬拼人头。
     * 屠边贴合线下玩法 —— 狼队要么把平民清干净，要么把神职清干净，
     * 每一刀、每一次投票都得想清楚"我们离哪条边更近"。</p>
     *
     * <p><b>先判"狼全死"</b>：万一最后一个狼和最后一批好人同归于尽
     * （比如猎人开枪带走最后的狼，而平民也已经死光），应该算好人赢 ——
     * 狼是"被杀光才算输"的那一方，先判它。</p>
     */
    private boolean checkWinCondition() {
        if (state.countAliveByCamp(Camp.WOLF) == 0) {
            state.setWinnerCamp(Camp.GOOD.name());
            return true;
        }

        long aliveVillagers = state.alivePlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER)
                .count();
        // 神职 = 好人阵营里不是平民的那些（预言家 / 女巫 / 猎人…）
        long aliveGods = state.alivePlayers().stream()
                .filter(p -> p.getRole() != null
                        && p.getRole().getCamp() == Camp.GOOD
                        && p.getRole() != Role.VILLAGER)
                .count();

        if (aliveVillagers == 0 || aliveGods == 0) {
            state.setWinnerCamp(Camp.WOLF.name());
            return true;
        }
        return false;
    }

    /** 结束游戏：公开所有人的身份 */
    private void endGame(List<GameEvent> events) {
        state.setPhase(GamePhase.FINISHED);
        String label;
        if (Camp.WOLF.name().equals(state.getWinnerCamp())) {
            label = "狼人阵营获胜";
        } else if (Camp.GOOD.name().equals(state.getWinnerCamp())) {
            label = "好人阵营获胜";
        } else {
            label = "平局（达到最大轮数 " + MAX_ROUNDS + " 轮）";
        }
        state.addMessage("游戏结束：" + label);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("winnerCamp", state.getWinnerCamp());
        data.put("winnerLabel", label);
        data.put("rounds", state.getRound());

        List<Map<String, Object>> reveal = new ArrayList<>();
        for (WerewolfPlayer player : state.getPlayers()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seatNo", player.getSeatNo());
            item.put("nickname", player.getNickname());
            item.put("role", player.getRole().name());
            item.put("roleLabel", player.getRole().getLabel());
            item.put("camp", player.getRole().getCamp().name());
            item.put("alive", player.isAlive());
            reveal.add(item);
        }
        data.put("players", reveal);

        events.add(GameEvent.publicEvent("GAME_END", data, "游戏结束：" + label));
        log.info("对局 {} 结束：{}，共 {} 轮", state.getGameId(), label, state.getRound());
    }

    // ============================================================
    // 视图
    // ============================================================

    @Override
    public GameView viewFor(int seatNo) {
        return GameView.of(WerewolfViews.publicState(state), buildPrivateState(seatNo));
    }

    /** 构造某个座位的私人状态——别人看不到的东西一个都不会放进去 */
    private PlayerPrivateState buildPrivateState(int seatNo) {
        PlayerPrivateState view = new PlayerPrivateState();
        view.setMySeat(seatNo);

        WerewolfPlayer me = state.findBySeat(seatNo);
        if (me == null || me.getRole() == null) {
            view.setAlive(false);
            return view;
        }

        view.setAlive(me.isAlive());
        view.setMyRole(me.getRole().name());
        view.setMyRoleLabel(me.getRole().getLabel());
        view.setMyCamp(me.getRole().getCamp().name());

        // 狼人：能看到队友是谁，以及狼队各自刀了谁（好商量今晚刀哪个）
        if (me.isWolf()) {
            view.setTeammateSeats(state.wolfSeats().stream()
                    .filter(seat -> seat != seatNo)
                    .collect(Collectors.toList()));

            Map<String, Integer> wolfVotes = new LinkedHashMap<>();
            for (Map.Entry<Integer, Integer> entry : state.getWolfVotes().entrySet()) {
                wolfVotes.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            view.putInfo("wolfVotes", wolfVotes);
            Integer myVote = state.getWolfVotes().get(seatNo);
            if (myVote != null) {
                view.putInfo("myWolfVote", myVote);
            }
            if (state.getWolfTarget() != null) {
                view.putInfo("wolfTarget", state.getWolfTarget());
            }
        }

        // 预言家：自己的查验记录
        if (me.getRole() == Role.SEER) {
            view.putInfo("seerResults", new LinkedHashMap<>(state.getSeerResults()));
        }

        // 女巫：药水还剩没有 + 今晚谁被袭击
        if (me.getRole() == Role.WITCH) {
            view.putInfo("saveUsed", state.isWitchSaveUsed());
            view.putInfo("poisonUsed", state.isWitchPoisonUsed());
            if (state.getWolfTarget() != null && !state.isWitchActed()) {
                view.putInfo("wolfTargetSeat", state.getWolfTarget());
            }
        }

        // 人设：大模型靠它写出各自的语气，规则 AI 也能用它调整行为倾向
        if (me.getPersona() != null) {
            view.putInfo("personaTag", me.getPersona().getTag());
            view.putInfo("personaStyle", me.getPersona().describe());
            view.putInfo("aggression", me.getPersona().getAggression());
        }

        // 跨局记忆：只给 AI —— 让它记得"上一局 3 号是狼，把我骗了"
        if (me.isAi() && !state.getRoomHistory().isEmpty()) {
            view.putInfo("roomHistory", new ArrayList<>(state.getRoomHistory()));
        }

        view.setAvailableActions(optionsFor(seatNo));
        view.setHint(hintFor(me));
        return view;
    }

    @Override
    public PublicGameState publicState() {
        return WerewolfViews.publicState(state);
    }

    @Override
    public List<String> optionsFor(int seatNo) {
        List<String> options = new ArrayList<>();
        WerewolfPlayer me = state.findBySeat(seatNo);
        if (me == null || me.getRole() == null) {
            return options;
        }
        if (!me.isAlive()) {
            // 猎人等着开枪 —— 他虽然是"出局的人"，但这一步恰恰该他动。
            // 这里必须在"遗言"判断之前：不然他的开枪按钮永远不会出现。
            if (state.getPhase() == GamePhase.HUNTER_SHOOT
                    && pendingHunterSeat != null && seatNo == pendingHunterSeat) {
                options.add(ActionType.HUNTER_SHOOT.name());
                options.add(ActionType.PASS.name());
                return options;
            }
            // 出局的人只有在"说遗言"时才有操作
            if (state.getPhase() == GamePhase.DAY_DISCUSSION
                    && WerewolfViews.currentSpeakerSeat(state) == seatNo) {
                options.add(ActionType.SPEAK.name());
                options.add(ActionType.PASS.name());
            }
            return options;
        }

        switch (state.getPhase()) {
            case NIGHT -> {
                // 夜晚分三步走：只有"当前这一步该行动的人"才有选项
                NightStep step = state.getNightStep();
                if (step == NightStep.WOLF && me.isWolf() && !state.getWolfVotes().containsKey(seatNo)) {
                    options.add(ActionType.WEREWOLF_KILL.name());
                }
                if (step == NightStep.WITCH && me.getRole() == Role.WITCH && !state.isWitchActed()) {
                    // 女巫可以自救，所以这里不排除"被刀的是她自己"的情况
                    if (!state.isWitchSaveUsed() && state.getWolfTarget() != null) {
                        options.add(ActionType.WITCH_SAVE.name());
                    }
                    if (!state.isWitchPoisonUsed()) {
                        options.add(ActionType.WITCH_POISON.name());
                    }
                    options.add(ActionType.PASS.name());
                }
                if (step == NightStep.SEER && me.getRole() == Role.SEER && !state.isSeerActed()) {
                    options.add(ActionType.SEER_CHECK.name());
                }
            }
            case DAY_DISCUSSION -> {
                if (WerewolfViews.currentSpeakerSeat(state) == seatNo) {
                    options.add(ActionType.SPEAK.name());
                    options.add(ActionType.PASS.name());
                }
            }
            case DAY_VOTE -> {
                if (!state.getDayVotes().containsKey(seatNo)
                        && !state.getAbstainedSeats().contains(seatNo)) {
                    options.add(ActionType.VOTE.name());
                    options.add(ActionType.PASS.name());
                }
            }
            default -> {
                // WAITING / DAY / FINISHED 阶段不需要玩家操作
            }
        }
        return options;
    }

    private String hintFor(WerewolfPlayer me) {
        GamePhase phase = state.getPhase();
        if (phase == GamePhase.FINISHED) {
            return "游戏已结束";
        }
        if (!me.isAlive()) {
            return "你已经出局了，可以继续旁观";
        }

        return switch (phase) {
            case NIGHT -> nightHintFor(me);
            case DAY -> "天亮了，正在公布昨晚的情况…";
            case DAY_DISCUSSION -> WerewolfViews.currentSpeakerSeat(state) == me.getSeatNo()
                    ? "轮到你发言了"
                    : "等待其他玩家发言…";
            case DAY_VOTE -> state.getDayVotes().containsKey(me.getSeatNo())
                    ? "你已投票，等待其他人…"
                    : "请投票选出你要放逐的人";
            case WAITING -> "等待房主开始游戏";
            default -> "";
        };
    }

    /** 夜晚的提示：只说"现在轮到你做什么"，不暴露别人的进度 */
    private String nightHintFor(WerewolfPlayer me) {
        return switch (state.getNightStep()) {
            case WOLF -> me.isWolf()
                    ? (state.getWolfVotes().containsKey(me.getSeatNo())
                        ? "已选择目标，等待队友…"
                        : "请选择今晚要击杀的玩家")
                    : "天黑请闭眼，等待其他角色行动…";
            case WITCH -> {
                if (me.getRole() != Role.WITCH) {
                    yield "天黑请闭眼，等待其他角色行动…";
                }
                if (state.isWitchActed()) {
                    yield "已做出决定，等待天亮…";
                }
                if (state.getWolfTarget() != null) {
                    yield "今晚 " + state.getWolfTarget() + " 号被袭击了。你可以用解药救他、用毒药毒一个人，或者选择不用药";
                }
                yield "等待狼人行动…";
            }
            case SEER -> me.getRole() == Role.SEER
                    ? (state.isSeerActed() ? "查验完成，等待天亮…" : "请选择今晚要查验的玩家")
                    : "天黑请闭眼，等待其他角色行动…";
        };
    }

    @Override
    public boolean isFinished() {
        return state.getPhase() == GamePhase.FINISHED;
    }

    @Override
    public GamePhase phase() {
        return state.getPhase();
    }

    @Override
    public String winnerCamp() {
        return state.getWinnerCamp();
    }

    // ============================================================
    // 校验辅助
    // ============================================================

    /** 夜晚行动的通用校验（阶段、存活、身份、是否已行动） */
    private ActionResult requireNightAction(WerewolfPlayer player, Role role, String roleName) {
        if (state.getPhase() != GamePhase.NIGHT) {
            return ActionResult.fail("现在不是夜晚，不能行动");
        }
        // 夜晚按「狼人 → 女巫 → 预言家」依次进行，没轮到的不能抢着动
        NightStep expected = switch (role) {
            case WOLF -> NightStep.WOLF;
            case WITCH -> NightStep.WITCH;
            case SEER -> NightStep.SEER;
            default -> null;
        };
        if (expected != null && state.getNightStep() != expected) {
            return ActionResult.fail("现在还没轮到" + roleName + "行动");
        }
        if (!player.isAlive()) {
            return ActionResult.fail("你已经出局了，不能行动");
        }
        if (player.getRole() != role) {
            return ActionResult.fail("你不是" + roleName + "，不能做这个动作");
        }
        if (player.isActedTonight()) {
            return ActionResult.fail("你今晚已经行动过了");
        }
        return null;
    }

    private ActionResult requireWitchAction(WerewolfPlayer player) {
        if (state.getPhase() != GamePhase.NIGHT) {
            return ActionResult.fail("现在不是夜晚，不能用药");
        }
        if (state.getNightStep() != NightStep.WITCH) {
            return ActionResult.fail("现在还没轮到女巫行动");
        }
        if (!player.isAlive()) {
            return ActionResult.fail("你已经出局了，不能用药");
        }
        if (player.getRole() != Role.WITCH) {
            return ActionResult.fail("你不是女巫，不能用药");
        }
        if (player.isActedTonight()) {
            return ActionResult.fail("你今晚已经做过决定了");
        }
        return null;
    }

    private WerewolfPlayer requireAliveTarget(Integer seatNo, String message) {
        if (seatNo == null) {
            return null;
        }
        WerewolfPlayer target = state.findBySeat(seatNo);
        if (target == null || !target.isAlive()) {
            return null;
        }
        return target;
    }
}
