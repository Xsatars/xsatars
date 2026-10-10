package com.aitable.service.impl;

import com.aitable.common.BusinessException;
import com.aitable.common.UserContext;
import com.aitable.common.enums.GameStatus;
import com.aitable.common.enums.Role;
import com.aitable.common.enums.RoomStatus;
import com.aitable.common.event.RoomChangedEvent;
import com.aitable.entity.Game;
import com.aitable.entity.GameRecord;
import com.aitable.entity.Player;
import com.aitable.entity.Room;
import com.aitable.entity.User;
import com.aitable.game.ai.AiBrain;
import com.aitable.game.ai.AiBrainRouter;
import com.aitable.game.ai.AiPersona;
import com.aitable.game.ai.AiReflector;
import com.aitable.game.ai.HostContext;
import com.aitable.game.ai.HostNarrator;
import com.aitable.game.ai.ReflectContext;
import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionResult;
import com.aitable.game.core.ActionType;
import com.aitable.game.core.GameEvent;
import com.aitable.game.core.GameManager;
import com.aitable.game.core.GamePhase;
import com.aitable.game.core.GameRuntime;
import com.aitable.game.core.GameView;
import com.aitable.game.werewolf.Board;
import com.aitable.game.werewolf.WerewolfEngine;
import com.aitable.game.werewolf.WerewolfPlayer;
import com.aitable.game.werewolf.WerewolfState;
import com.aitable.mapper.GameMapper;
import com.aitable.mapper.GameRecordMapper;
import com.aitable.mapper.PlayerMapper;
import com.aitable.mapper.RoomMapper;
import com.aitable.mapper.UserMapper;
import com.aitable.service.GameService;
import com.aitable.service.RoomService;
import jakarta.annotation.PreDestroy;
import com.aitable.websocket.WsEventPublisher;
import com.aitable.websocket.WsMessageType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 游戏服务实现。
 *
 * <h3>这里有一条很重要的并发规则</h3>
 * <p>WebSocket 消息是<b>多个线程同时</b>处理的（6 个人可能同时点投票）。
 * 所以我们把每局的操作分成两步：</p>
 * <ol>
 *   <li><b>锁内</b>：执行行动 + 读取最新状态 + 把"要发哪些消息"全部准备好</li>
 *   <li><b>锁外</b>：真正把消息发出去（网络 IO 不占着锁）</li>
 * </ol>
 * <p>一开始我把"读状态生成视图"放在锁外，结果出现两种事故：
 * 同一次结束被 3 个线程各写了一遍战绩；以及读状态时撞上别人正在改集合，
 * 直接抛 ConcurrentModificationException。改成现在这样就干净了。</p>
 *
 * <p>注意这个类里<b>没有任何游戏规则判断</b>——规则全在 {@link WerewolfEngine}。</p>
 */
@Service
public class GameServiceImpl implements GameService {

    private static final Logger log = LoggerFactory.getLogger(GameServiceImpl.class);

    private final RoomMapper roomMapper;
    private final PlayerMapper playerMapper;
    private final UserMapper userMapper;
    private final GameMapper gameMapper;
    private final GameRecordMapper gameRecordMapper;
    private final RoomService roomService;
    private final GameManager gameManager;
    private final WsEventPublisher publisher;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final AiBrainRouter aiBrainRouter;
    private final HostNarrator hostNarrator;
    private final AiReflector aiReflector;

    /**
     * 每局用哪个"脑子"（开局那一刻定下，中途不换）。
     *
     * <p>为什么缓存而不是每次查库：AI 每一步都要决策，一次对局几十步，
     * 每步查一次房间太浪费。而且<b>中途不让换</b>也是刻意的 ——
     * 同一个 AI 前半局一个样、后半局一个样，玩家会觉得精神分裂。</p>
     */
    private final Map<Long, AiBrain> gameBrains = new ConcurrentHashMap<>();

    /** 每个需要玩家操作的步骤最多等多少秒（超时自动跳过） */
    @Value("${ai-table.game.action-timeout-seconds:90}")
    private int actionTimeoutSeconds;

    /**
     * 防止"AI 处理"递归。
     *
     * <p>AI 行动后也要广播，而广播出口又会去检查"有没有 AI 要动"，
     * 这样就套娃了。用一个线程内的标记挡住：已经是 AI 在行动的时候，
     * 下一步由 {@code runAiTurn} 自己安排，不需要广播出口再插一脚。</p>
     */
    private final ThreadLocal<Boolean> aiTurnRunning = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** AI 每次行动之间隔多少毫秒 —— 太快了玩家看不清它在干什么 */
    @Value("${ai-table.ai.action-delay-ms:1500}")
    private long aiActionDelayMs;

    /** AI 发言之后隔多少毫秒 —— 留时间让人把话读完 */
    @Value("${ai-table.ai.speak-delay-ms:3500}")
    private long aiSpeakDelayMs;

    /**
     * "换场"时停多久（毫秒）。
     *
     * <p>换场 = 夜晚的狼人→女巫→预言家交接、白天的发言→投票切换、白天↔夜晚的转换。
     * 这些时刻比普通动作更需要停顿，玩家才知道"换人了 / 换阶段了"。</p>
     */
    @Value("${ai-table.ai.phase-delay-ms:5000}")
    private long aiPhaseDelayMs;

    /**
     * 投票阶段 AI 之间的间隔（毫秒）。
     *
     * <p>投票是"大家一起投"，要利索点投完 —— 6 个人 × 5 秒换场停顿就半分钟过去了，
     * 全桌都在干等。</p>
     */
    @Value("${ai-table.ai.vote-delay-ms:800}")
    private long aiVoteDelayMs;

    /**
     * 记一下每局"上一次 AI 出手时处于哪个阶段"。
     *
     * <p>用它来判断"这次是不是刚换过场" —— 换场了就该停久一点。</p>
     */
    private final Map<Long, String> lastAiPhase = new ConcurrentHashMap<>();

    /**
     * AI 思考用的调度器。
     *
     * <p>单线程：一桌 AI 排队说话，不会抢。守护线程：不影响进程退出。</p>
     */
    private final ScheduledExecutorService aiScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "ai-player");
                thread.setDaemon(true);
                return thread;
            });

    /** 每一局最多挂一个"待执行的 AI 行动"，用来防止重复调度 */
    private final Map<Long, ScheduledFuture<?>> aiTurnTasks = new ConcurrentHashMap<>();

    public GameServiceImpl(RoomMapper roomMapper, PlayerMapper playerMapper, UserMapper userMapper,
                           GameMapper gameMapper, GameRecordMapper gameRecordMapper,
                           RoomService roomService, GameManager gameManager,
                           WsEventPublisher publisher, ApplicationEventPublisher eventPublisher,
                           TransactionTemplate transactionTemplate, AiBrainRouter aiBrainRouter,
                           HostNarrator hostNarrator, AiReflector aiReflector) {
        this.roomMapper = roomMapper;
        this.playerMapper = playerMapper;
        this.userMapper = userMapper;
        this.gameMapper = gameMapper;
        this.gameRecordMapper = gameRecordMapper;
        this.roomService = roomService;
        this.gameManager = gameManager;
        this.publisher = publisher;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.aiBrainRouter = aiBrainRouter;
        this.hostNarrator = hostNarrator;
        this.aiReflector = aiReflector;
    }

    // ============================================================
    // 开局
    // ============================================================

    @Override
    public Long startGame(Long roomId) {
        Long userId = UserContext.getUserId();

        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        if (!room.getOwnerUserId().equals(userId)) {
            throw new BusinessException("只有房主可以开始游戏");
        }
        if (!RoomStatus.WAITING.name().equals(room.getStatus())) {
            throw new BusinessException("房间已经在对局中或已结束");
        }

        List<Player> seats = listPlayers(roomId);
        if (seats.size() != room.getMaxPlayers()) {
            throw new BusinessException("人数不足：还需要 " + (room.getMaxPlayers() - seats.size()) + " 名玩家");
        }
        // 满员还不够，除房主外每个人都要点过"准备"
        long notReady = seats.stream()
                .filter(seat -> seat.getReady() == null || seat.getReady() == 0)
                .count();
        if (notReady > 0) {
            throw new BusinessException("还有 " + notReady + " 人没有准备，等他们点「准备」再开局");
        }

        // 建对局记录，并把房间置为"对局中"
        Game game = new Game();
        game.setRoomId(roomId);
        game.setGameType(room.getGameType());
        game.setBoardCode(room.getBoardCode());
        game.setStatus(GameStatus.PLAYING.name());
        game.setTotalRounds(0);
        game.setStartedAt(LocalDateTime.now());
        gameMapper.insert(game);

        room.setStatus(RoomStatus.PLAYING.name());
        room.setCurrentGameId(game.getId());
        roomMapper.updateById(room);
        publishRoomChanged(roomId);

        // 组装引擎：按房间选定的板子来（6 人局 / 8 人局）
        Board board = Board.byCode(room.getBoardCode());
        WerewolfEngine engine = WerewolfEngine.create(game.getId(), roomId, buildPlayers(seats),
                actionTimeoutSeconds, board);

        // 定下这局 AI 用哪个"脑子"（规则 / 大模型）。
        // 在开局这一刻定，是为了让同一局里 AI 的行为前后一致 ——
        // 中途换脑子会让它前一句还在装傻、后一句突然精算，很出戏。
        gameBrains.put(game.getId(), aiBrainRouter.forRoom(room.getAiBrain()));
        // 跨局记忆：把这个房间之前几局的战绩装进去，AI 才会有"上一局谁骗过我"的印象
        engine.loadRoomHistory(loadRoomHistory(roomId));
        GameRuntime runtime = new GameRuntime(game.getId(), roomId, engine);
        gameManager.register(runtime);

        // 锁内：发牌 + 准备要发的消息（身份牌是私有消息，只有本人收得到）
        DispatchPlan plan = runtime.locked(() -> {
            List<GameEvent> events = engine.start();
            DispatchPlan built = new DispatchPlan();
            built.result = ActionResult.ok("游戏开始", events);
            fillPlan(built, runtime, events);
            return built;
        });
        executePlan(runtime, plan);

        log.info("房间 {} 开始对局 {}，共 {} 人", roomId, game.getId(), seats.size());
        return game.getId();
    }

    // ============================================================
    // 行动
    // ============================================================

    @Override
    public void submitAction(Long gameId, Long userId, ActionType type, Integer targetSeat, String text) {
        GameRuntime runtime = gameManager.require(gameId);
        WerewolfEngine engine = (WerewolfEngine) runtime.getEngine();

        // 锁内：执行行动 + 生成要发的所有消息 + 判断这局是不是刚结束
        DispatchPlan plan = runtime.locked(() -> {
            int seatNo = seatOf(runtime, userId);
            ActionRequest request = type == ActionType.SPEAK
                    ? ActionRequest.speak(seatNo, text)
                    : ActionRequest.of(seatNo, type, targetSeat);

            ActionResult result = engine.apply(request);

            DispatchPlan built = new DispatchPlan();
            built.result = result;
            if (result.isSuccess()) {
                fillPlan(built, runtime, result.getEvents());
                // markFinished 是原子的：多个人同时让游戏结束时，只有一个人能拿到 true
                built.finished = engine.isFinished() && runtime.markFinished();
            }
            return built;
        });

        if (!plan.result.isSuccess()) {
            // 不合法：状态一点没动，只把原因回给发起者
            publisher.sendError(userId, plan.result.getMessage());
            return;
        }

        // 锁外发消息
        executePlan(runtime, plan);

        if (plan.finished) {
            finishGame(runtime);
        }
    }

    @Override
    public GameView viewFor(Long gameId, Long userId) {
        GameRuntime runtime = gameManager.require(gameId);
        WerewolfEngine engine = (WerewolfEngine) runtime.getEngine();
        return runtime.locked(() -> engine.viewFor(seatOf(runtime, userId)));
    }

    @Override
    public boolean handleTimeoutIfNeeded(Long gameId) {
        GameRuntime runtime = gameManager.find(gameId);
        if (runtime == null) {
            // 对局已经结束并从内存里移除了
            return false;
        }
        WerewolfEngine engine = (WerewolfEngine) runtime.getEngine();

        DispatchPlan plan = runtime.locked(() -> {
            // 进锁后再判断一次：可能刚好有人在这之前操作了，超时时间已经被推后
            if (!engine.isTimeout()) {
                return null;
            }
            ActionResult result = engine.timeout();

            DispatchPlan built = new DispatchPlan();
            built.result = result;
            if (result.isSuccess()) {
                fillPlan(built, runtime, result.getEvents());
                built.finished = engine.isFinished() && runtime.markFinished();
            }
            return built;
        });

        if (plan == null) {
            return false;
        }

        executePlan(runtime, plan);
        if (plan.finished) {
            finishGame(runtime);
        }
        log.info("对局 {} 有玩家超时未操作，已自动跳过当前步骤", gameId);
        return true;
    }

    @Override
    public GameView currentGameFor(Long userId) {
        for (GameRuntime runtime : gameManager.all()) {
            WerewolfEngine engine = (WerewolfEngine) runtime.getEngine();
            WerewolfState state = engine.getState();
            // 注意判空：AI 玩家没有 userId，直接 .equals 会 NPE
            boolean inThisGame = state.getPlayers().stream()
                    .anyMatch(player -> player.getUserId() != null
                            && player.getUserId().equals(userId));
            if (!inThisGame || engine.phase() == GamePhase.FINISHED) {
                continue;
            }
            try {
                return runtime.locked(() -> engine.viewFor(seatOf(runtime, userId)));
            } catch (BusinessException e) {
                // 理论上不会发生，稳妥起见跳过
            }
        }
        return null;
    }

    @Override
    public void markOnline(Long userId) {
        for (GameRuntime runtime : gameManager.all()) {
            WerewolfState state = ((WerewolfEngine) runtime.getEngine()).getState();
            WerewolfPlayer player = playerOf(state, userId);
            if (player == null || player.isOnline()) {
                continue;
            }
            runtime.locked(() -> {
                player.setOnline(true);
                return null;
            });
            broadcastViews(runtime);
            log.info("玩家 {} 重新回到对局 {}", userId, runtime.getGameId());
        }
    }

    @Override
    public void markOffline(Long userId) {
        for (GameRuntime runtime : gameManager.all()) {
            WerewolfEngine engine = (WerewolfEngine) runtime.getEngine();
            WerewolfState state = engine.getState();
            WerewolfPlayer player = playerOf(state, userId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            DispatchPlan plan = runtime.locked(() -> {
                player.setOnline(false);

                // 如果正等着他操作，立刻替他跳过（不用让全桌干等 90 秒）
                ActionResult result = engine.forfeit(player.getSeatNo());

                DispatchPlan built = new DispatchPlan();
                built.result = result;
                if (result.isSuccess() && !result.getEvents().isEmpty()) {
                    fillPlan(built, runtime, result.getEvents());
                    built.finished = engine.isFinished() && runtime.markFinished();
                } else {
                    // 没有需要跳过的动作，也要广播一次，让其他人看到"离线"
                    fillPlan(built, runtime, List.of());
                }
                return built;
            });

            executePlan(runtime, plan);
            if (plan.finished) {
                finishGame(runtime);
            }
            log.info("玩家 {} 掉线（对局 {}），已标记离线", userId, runtime.getGameId());
        }
    }

    @Override
    public void forceCloseGame(Long gameId) {
        GameRuntime runtime = gameManager.find(gameId);

        if (runtime == null) {
            // 对局可能已经结束了，只把数据库补上
            transactionTemplate.executeWithoutResult(status -> {
                Game game = gameMapper.selectById(gameId);
                if (game != null && !GameStatus.FINISHED.name().equals(game.getStatus())) {
                    game.setStatus(GameStatus.FINISHED.name());
                    game.setEndedAt(LocalDateTime.now());
                    gameMapper.updateById(game);
                }
            });
            return;
        }

        WerewolfState state = ((WerewolfEngine) runtime.getEngine()).getState();
        Long roomId = runtime.getRoomId();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("terminated", true);
        payload.put("winnerCamp", null);
        payload.put("winnerLabel", "对局已被管理员终止");
        payload.put("rounds", state.getRound());
        payload.put("players", List.of());
        publisher.broadcastToRoom(roomId, WsMessageType.GAME_END, payload, "对局已被管理员终止");

        transactionTemplate.executeWithoutResult(status -> {
            Game game = gameMapper.selectById(gameId);
            if (game != null) {
                game.setStatus(GameStatus.FINISHED.name());
                game.setTotalRounds(state.getRound());
                game.setEndedAt(LocalDateTime.now());
                gameMapper.updateById(game);
            }
        });

        gameManager.remove(gameId);
        log.info("对局 {} 已被管理员强制终止", gameId);
    }

    /** 把最新的视图广播给局里所有人（用于"谁上线/掉线了"这类变化） */
    private void broadcastViews(GameRuntime runtime) {
        DispatchPlan plan = runtime.locked(() -> {
            DispatchPlan built = new DispatchPlan();
            built.result = ActionResult.ok("状态更新");
            fillPlan(built, runtime, List.of());
            return built;
        });
        executePlan(runtime, plan);
    }

    private WerewolfPlayer playerOf(WerewolfState state, Long userId) {
        if (userId == null) {
            return null;
        }
        return state.getPlayers().stream()
                .filter(player -> userId.equals(player.getUserId()))
                .findFirst()
                .orElse(null);
    }

    // ============================================================
    // 消息准备与投递
    // ============================================================

    /**
     * 【必须在锁内调用】把引擎产出的事件整理成"待发送的消息清单"。
     *
     * <ul>
     *   <li>公开事件 → 房间频道（所有人都能看到）</li>
     *   <li>私有事件 → 对应玩家的私人频道（例如查验结果只有预言家收到）</li>
     *   <li>再给每个人补一份他自己的最新视图（公共状态 + 他的私有信息和可用动作）</li>
     * </ul>
     */
    private void fillPlan(DispatchPlan plan, GameRuntime runtime, List<GameEvent> events) {
        WerewolfEngine engine = (WerewolfEngine) runtime.getEngine();
        WerewolfState state = engine.getState();

        for (GameEvent event : events) {
            WsMessageType messageType = WsMessageType.from(event.getType());
            if (messageType == null) {
                continue;
            }
            if (event.isPublicEvent()) {
                plan.publicMessages.add(OutgoingMessage.toRoom(messageType, event.getPayload(), event.getMessage()));
            } else {
                Long targetUserId = userIdOfSeat(state, event.getOwnerSeat());
                if (targetUserId != null) {
                    plan.privateMessages.add(OutgoingMessage.toUser(targetUserId, messageType,
                            event.getPayload(), event.getMessage()));
                }
            }
        }

        // 主持人在关键节点说一句（天亮 / 放逐 / 结束）——
        // 放在"同步局面"之前，前端先看到解说词，再刷新状态
        hostSpeak(runtime, events, plan);

        for (WerewolfPlayer player : state.getPlayers()) {
            plan.privateMessages.add(OutgoingMessage.toUser(player.getUserId(),
                    WsMessageType.GAME_STATE_UPDATE, engine.viewFor(player.getSeatNo()), null));
        }
    }

    // ============================================================
    // 主持人（上帝视角的解说）
    // ============================================================

    /**
     * 让主持人在关键节点说一句。
     *
     * <p>只在"值得解说"的事件上开口 —— 每个夜晚都念叨会烦。
     * 讲不出来（没接大模型、网络失败、模型胡说）就用事件自带的兜底文案，
     * 所以主持人<b>永远不会沉默</b>。</p>
     */
    private void hostSpeak(GameRuntime runtime, List<GameEvent> events, DispatchPlan plan) {
        WerewolfState state = ((WerewolfEngine) runtime.getEngine()).getState();
        for (GameEvent event : events) {
            if (!event.isPublicEvent()) {
                continue;
            }
            String moment = switch (event.getType() == null ? "" : event.getType()) {
                // NIGHT_RESULT 这个类型历史上被好几处用过（比如狼队分票时也发过一条）。
                // 主持人只该认"天亮结算"那一条 —— 判断依据是它带不带 deaths。
                // 否则会出现"明明死了人却说成平安夜"这种乌龙（真实踩过）。
                case "NIGHT_RESULT" -> hasDeaths(event) ? "DAWN" : null;
                case "VOTE_RESULT" -> "VOTE";
                case "GAME_END" -> "GAME_END";
                default -> null;
            };
            if (moment == null) {
                continue;
            }

            HostContext context = buildHostContext(moment, state, event);
            String words = null;
            try {
                words = hostNarrator.narrate(context);
            } catch (Exception e) {
                log.warn("主持人解说失败，改用兜底文案", e);
            }
            if (words == null || words.isBlank()) {
                words = context.getFallbackText();
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("text", words);
            payload.put("moment", moment);
            payload.put("round", state.getRound());
            plan.publicMessages.add(OutgoingMessage.toRoom(WsMessageType.HOST_SAY, payload, words));
            state.addMessage("🎙 " + words);
            log.info("🎙 主持人（{}）：{}", moment, words);
            return;   // 一次只讲一句，不刷屏
        }
    }

    /**
     * 攒一份"给主持人看的素材"。
     *
     * <p><b>只放公开信息</b>：谁出局了、还剩几个人、哪边赢了。
     * 身份不给 —— 主持人说的话全场都能听到，给了他就会说漏。</p>
     */
    private HostContext buildHostContext(String moment, WerewolfState state, GameEvent event) {
        HostContext context = new HostContext();
        context.setMoment(moment);
        context.setRound(state.getRound());
        context.setPhaseLabel(state.getPhase() == null ? null : state.getPhase().name());
        context.setFallbackText(event.getMessage() == null ? "（主持人沉默了）" : event.getMessage());

        // 从**事件**里取昨夜出局的名单，而不是从 state 上取。
        // 原因：state.lastNightDeaths 只是为了让死者先发遗言临时存的，
        // 走到这里时已经被清掉了（曾经因此让主持人把死人说成"平安夜"）。
        // 事件是不可变的，它带的数据才靠谱。
        context.setDeaths(extractDeaths(state, event));

        // 同理，放逐名单也从事件里取
        context.setExiled(extractExiled(state, event));
        context.setWinnerCamp(state.getWinnerCamp());

        StringBuilder alive = new StringBuilder();
        StringBuilder dead = new StringBuilder();
        for (WerewolfPlayer player : state.getPlayers()) {
            StringBuilder target = player.isAlive() ? alive : dead;
            if (target.length() > 0) {
                target.append('、');
            }
            target.append(player.getSeatNo()).append(" 号");
        }
        context.setAliveSeats(alive.toString());
        context.setDeadSeats(dead.toString());
        return context;
    }

    /** 这条事件带没带"昨夜死亡名单" —— 只有真正的天亮结算才带 */
    private boolean hasDeaths(GameEvent event) {
        return event.getPayload() instanceof Map<?, ?> payload && payload.containsKey("deaths");
    }

    /**
     * 从 {@code NIGHT_RESULT} 事件里取出"昨夜谁出局了"。
     *
     * <p>别再回头去读 {@code state.getLastNightDeaths()} —— 那个字段是给
     * "让死者先发遗言"用的，处理到这个事件时早被清空了。
     * 之前的版本就是这么错的：明明死了人，主持人却说"平安夜"。</p>
     */
    private List<String> extractDeaths(WerewolfState state, GameEvent event) {
        List<String> deaths = new ArrayList<>();
        if (!(event.getPayload() instanceof Map<?, ?> payload)) {
            return deaths;
        }
        if (!(payload.get("deaths") instanceof List<?> seats)) {
            return deaths;
        }
        for (Object item : seats) {
            if (item == null) {
                continue;
            }
            try {
                int seat = Integer.parseInt(String.valueOf(item).trim());
                WerewolfPlayer dead = state.findBySeat(seat);
                deaths.add(seat + " 号" + (dead == null ? "" : " " + dead.getNickname()));
            } catch (NumberFormatException ignored) {
                // 事件里塞了奇怪的东西就跳过，不影响主持人说话
            }
        }
        return deaths;
    }

    /** 从 {@code VOTE_RESULT} 事件里取出"谁被放逐了"；没人出局返回 null */
    private String extractExiled(WerewolfState state, GameEvent event) {
        if (!(event.getPayload() instanceof Map<?, ?> payload)) {
            return null;
        }
        Object exiled = payload.get("exiledSeat");
        if (exiled == null) {
            return null;
        }
        try {
            int seat = Integer.parseInt(String.valueOf(exiled).trim());
            WerewolfPlayer player = state.findBySeat(seat);
            return seat + " 号" + (player == null ? "" : " " + player.getNickname());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 【在锁外调用】真正把消息发出去 */
    private void executePlan(GameRuntime runtime, DispatchPlan plan) {
        for (OutgoingMessage message : plan.publicMessages) {
            publisher.broadcastToRoom(runtime.getRoomId(), message.type, message.payload, message.message);
        }
        for (OutgoingMessage message : plan.privateMessages) {
            publisher.sendToUser(message.userId, message.type, message.payload, message.message);
        }

        // 广播完了，安排 AI 出手。
        // 注意：如果这次本来就是 AI 在行动（aiTurnRunning=true），下一步由 runAiTurn 负责安排，
        // 这里不要再插一脚，否则会重复调度。
        if (!Boolean.TRUE.equals(aiTurnRunning.get())) {
            WerewolfEngine engine = (WerewolfEngine) runtime.getEngine();
            // 投票要利索（大家同时投），其它时候走正常的第一档停顿
            scheduleAiTurn(runtime,
                    engine.phase() == GamePhase.DAY_VOTE ? aiVoteDelayMs : aiPhaseDelayMs);
            lastAiPhase.put(runtime.getGameId(), currentPhaseKey(engine));
        }
    }

    // ============================================================
    // AI 玩家
    // ============================================================

    /**
     * 安排一次 AI 行动（延迟执行）。
     *
     * <p><b>为什么用调度器而不是 Thread.sleep</b>：AI 思考确实需要占用时间，
     * 但如果直接在这里 sleep，会把发起这次行动的<b>真人玩家的请求线程</b>一起卡住 ——
     * 他点一下按钮要等好几秒才收到响应。用调度器就只是"排个队"，谁都不阻塞。</p>
     *
     * <p>同一局同时只会有一个预约（{@code computeIfAbsent} 保证），
     * 不会出现两个 AI 抢着动、或者同一个动作被算两次的情况。</p>
     */
    private void scheduleAiTurn(GameRuntime runtime, long delayMs) {
        Long gameId = runtime.getGameId();
        aiTurnTasks.computeIfAbsent(gameId, id -> aiScheduler.schedule(
                () -> runAiTurn(runtime), Math.max(0, delayMs), TimeUnit.MILLISECONDS));
    }

    private void runAiTurn(GameRuntime runtime) {
        Long gameId = runtime.getGameId();
        // 先把这个预约销掉，后面才有资格安排下一次
        aiTurnTasks.remove(gameId);

        ActionType acted;
        try {
            aiTurnRunning.set(Boolean.TRUE);
            acted = aiTurn(runtime);
        } catch (Exception e) {
            log.error("AI 行动失败（对局 {}），本次跳过", gameId, e);
            return;
        } finally {
            aiTurnRunning.set(Boolean.FALSE);
        }

        if (acted == null) {
            return;   // 没有 AI 需要动了，让它安静下来
        }
        // 还有 AI 要动 → 隔一会儿再安排下一个
        scheduleAiTurn(runtime, nextDelayFor(runtime, acted));
    }

    /**
     * 下一步该等多久。
     *
     * <p>四种节奏，从慢到快：</p>
     * <ol>
     *   <li><b>刚换场</b>（默认 5 秒）：夜晚换人、发言→投票、白天↔夜晚 —— 让人看清"换阶段了"</li>
     *   <li><b>发言之后</b>（默认 4 秒）：留时间把话读完</li>
     *   <li><b>夜晚的同一步内</b>（默认 5 秒）：两个狼相继投票也算换人，同样要慢</li>
     *   <li><b>其它动作</b>（默认 2 秒）：投票、刀人这种不需要读文字</li>
     * </ol>
     */
    private long nextDelayFor(GameRuntime runtime, ActionType lastAction) {
        WerewolfEngine engine = (WerewolfEngine) runtime.getEngine();
        String phase = currentPhaseKey(engine);
        String previous = lastAiPhase.put(runtime.getGameId(), phase);

        // 投票阶段是"大家一起投"：AI 该利索点投完，
        // 这里**故意不用换场停顿** —— 不然 6 个人的投票会拖成半分钟，全桌干等
        if (engine.phase() == GamePhase.DAY_VOTE) {
            return aiVoteDelayMs;
        }
        // 刚换过场（夜晚换了角色，或者白天/夜晚切换了）→ 停久一点
        if (previous != null && !previous.equals(phase)) {
            return aiPhaseDelayMs;
        }
        if (engine.phase() == GamePhase.NIGHT) {
            return aiPhaseDelayMs;
        }
        return lastAction == ActionType.SPEAK ? aiSpeakDelayMs : aiActionDelayMs;
    }

    /**
     * 当前"场次"的标识。
     *
     * <p>把阶段和夜晚步骤拼起来用 —— 这样"夜晚里从狼人换到女巫"也算换场，
     * 玩家能看到"现在换人了"。</p>
     */
    private String currentPhaseKey(WerewolfEngine engine) {
        GamePhase phase = engine.phase();
        if (phase == GamePhase.NIGHT && engine.getState().getNightStep() != null) {
            return phase.name() + "/" + engine.getState().getNightStep().name();
        }
        return phase.name();
    }

    /**
     * 让一个 AI 走一步。
     *
     * @return 它做了什么；返回 {@code null} 表示当前没有 AI 需要行动
     */
    private ActionType aiTurn(GameRuntime runtime) {
        WerewolfEngine engine = (WerewolfEngine) runtime.getEngine();
        if (engine.isFinished()) {
            return null;
        }

        // 找一个"现在有动作可做"的 AI（按座位顺序，保证公平）
        WerewolfPlayer actor = null;
        for (WerewolfPlayer player : engine.getState().getPlayers()) {
            if (!player.isAi()) {
                continue;
            }
            if (!engine.optionsFor(player.getSeatNo()).isEmpty()) {
                actor = player;
                break;
            }
        }
        if (actor == null) {
            return null;
        }

        final int actorSeat = actor.getSeatNo();
        final ActionType[] actedType = new ActionType[1];
        DispatchPlan plan = runtime.locked(() -> {
            GameView view = engine.viewFor(actorSeat);
            // 这局用哪个脑子，开局就定好了（房间选规则 / 大模型）
            ActionRequest request = brainFor(runtime.getGameId()).decide(view);
            if (request == null) {
                return null;
            }
            ActionResult result = engine.apply(request);
            if (!result.isSuccess()) {
                log.warn("AI {} 号位的动作被引擎拒绝：{}", actorSeat, result.getMessage());
                return null;
            }
            actedType[0] = request.getType();
            DispatchPlan built = new DispatchPlan();
            built.result = result;
            fillPlan(built, runtime, result.getEvents());
            built.finished = engine.isFinished() && runtime.markFinished();
            return built;
        });

        if (plan == null) {
            return null;
        }

        executePlan(runtime, plan);   // 此时 aiTurnRunning=true，不会再触发调度
        if (plan.finished) {
            finishGame(runtime);
            return null;
        }
        return actedType[0];
    }

    // ============================================================
    // AI 的局后复盘
    // ============================================================

    /**
     * 让每个 AI 复盘自己这一局（返回：座位号 → 复盘内容）。
     *
     * <p><b>在事务外调用</b>，因为要调大模型。任何一个 AI 复盘失败都不影响结算 ——
     * 大不了它这局没有"教训"，下一局少一句台词而已。</p>
     */
    private Map<Integer, String> collectReflections(WerewolfState state) {
        Map<Integer, String> result = new HashMap<>();
        for (WerewolfPlayer player : state.getPlayers()) {
            if (!player.isAi()) {
                continue;
            }
            try {
                ReflectContext context = buildReflectContext(player, state);
                String text = aiReflector.reflect(context);
                if (text != null && !text.isBlank()) {
                    result.put(player.getSeatNo(), text);
                    log.info("🧠 {}（{} 号）复盘：{}", player.getNickname(), player.getSeatNo(), text);
                }
            } catch (Exception e) {
                log.warn("{} 号位的复盘失败，跳过", player.getSeatNo(), e);
            }
        }
        return result;
    }

    /** 攒一份"复盘素材"（这时候身份已经公开，可以放心给） */
    private ReflectContext buildReflectContext(WerewolfPlayer player, WerewolfState state) {
        ReflectContext context = new ReflectContext();
        context.setSeatNo(player.getSeatNo());
        context.setNickname(player.getNickname());
        context.setPersonaStyle(player.getPersona() == null
                ? null : player.getPersona().describe());
        context.setRoleLabel(player.getRole() == null ? "未知" : player.getRole().getLabel());
        context.setCamp(player.getRole() == null ? null : player.getRole().getCamp().name());
        context.setWin("WIN".equals(judgeResult(
                player.getRole() == null ? "" : player.getRole().getCamp().name(),
                state.getWinnerCamp())));
        context.setSurvived(player.isAlive());
        context.setRounds(state.getRound());

        if (!player.isAlive()) {
            // 出局的两种可能：夜里被刀，或者白天被投出去
            boolean votedOut = state.getExiledSeat() != null
                    && state.getExiledSeat() == player.getSeatNo();
            context.setDeathReason(votedOut ? "被投票放逐出局" : "夜里被刀出局");
        }

        // 只摘它自己说过的话 —— 复盘是自己的事
        List<String> mine = new ArrayList<>();
        for (String line : state.speechHistoryText(100)) {
            if (line.contains(player.getSeatNo() + " 号 " + player.getNickname())) {
                mine.add(line);
            }
        }
        context.setSpeeches(mine);
        context.setVotes(state.voteHistoryText(10));
        return context;
    }

    // ============================================================
    // 结束落库
    // ============================================================

    private void finishGame(GameRuntime runtime) {
        WerewolfEngine engine = (WerewolfEngine) runtime.getEngine();
        WerewolfState state = engine.getState();

        transactionTemplate.executeWithoutResult(status -> {
            Game game = gameMapper.selectById(runtime.getGameId());
            if (game != null) {
                game.setStatus(GameStatus.FINISHED.name());
                game.setTotalRounds(state.getRound());
                game.setWinnerCamp(state.getWinnerCamp());
                game.setEndedAt(LocalDateTime.now());
                gameMapper.updateById(game);
            }

            for (WerewolfPlayer player : state.getPlayers()) {
                GameRecord record = new GameRecord();
                record.setGameId(runtime.getGameId());
                record.setUserId(player.getUserId());
                // AI 没有账号，名字和性格单独记两列，这样战绩统计能看出"这局有机器人、什么脾气"
                record.setAiName(player.isAi() ? player.getNickname() : null);
                record.setAiPersona(player.isAi() && player.getPersona() != null
                        ? player.getPersona().getKey() : null);
                record.setSeatNo(player.getSeatNo());
                record.setRoleCode(player.getRole().name());
                record.setCamp(player.getRole().getCamp().name());
                record.setResult(judgeResult(player.getRole().getCamp().name(), state.getWinnerCamp()));
                record.setSurvived(player.isAlive() ? 1 : 0);
                gameRecordMapper.insert(record);
            }

            // 房间回到等待状态，可以再来一局。
            // 注意：MyBatis-Plus 的 updateById 默认会忽略 null 字段，
            // 所以"把 currentGameId 清空"这件事必须用 UpdateWrapper 显式 set null，
            // 否则房间会一直挂着一个已经结束的对局 ID。
            Room room = roomMapper.selectById(runtime.getRoomId());
            if (room != null) {
                roomMapper.update(null, new LambdaUpdateWrapper<Room>()
                        .eq(Room::getId, room.getId())
                        .set(Room::getStatus, RoomStatus.WAITING.name())
                        .set(Room::getCurrentGameId, null));

                // 新一局所有人都要重新准备。
                // 注意 AI 玩家没有 userId（是 null），直接 .equals 会 NPE；
                // 而且 AI 本来就不点准备，让它保持已就绪。
                List<Player> seats = playerMapper.selectList(
                        new LambdaQueryWrapper<Player>().eq(Player::getRoomId, room.getId()));
                for (Player seat : seats) {
                    boolean isOwner = seat.getUserId() != null
                            && seat.getUserId().equals(room.getOwnerUserId());
                    seat.setReady((isOwner || seat.isAiPlayer()) ? 1 : 0);
                    playerMapper.updateById(seat);
                }
            }
        });

        publishRoomChanged(runtime.getRoomId());
        gameManager.remove(runtime.getGameId());
        // 这局结束了，把它占着的脑子缓存也清掉（下一局开局时会重新按房间选）
        gameBrains.remove(runtime.getGameId());

        // 对局都结束了，把还没到点的 AI 预约取消掉，免得它对着一个已结束的对局再动一次
        ScheduledFuture<?> pending = aiTurnTasks.remove(runtime.getGameId());
        if (pending != null) {
            pending.cancel(false);
        }

        log.info("对局 {} 已结束并写入战绩（{} 轮，{} 获胜）",
                runtime.getGameId(), state.getRound(), state.getWinnerCamp());

        // ---------- 复盘放在最后 ----------
        // 复盘要调大模型，5 个 AI 加起来能花十几秒。
        // 放在最后是为了"房间已经复位、玩家已经能立刻再来一局" ——
        // 谁也不用等 AI 反省完才能继续玩。
        Map<Integer, String> reflections;
        try {
            reflections = collectReflections(state);
            if (!reflections.isEmpty()) {
                saveReflections(runtime.getGameId(), reflections);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("reflections", reflections);
                publisher.broadcastToRoom(runtime.getRoomId(),
                        WsMessageType.AI_REFLECTION, payload, "AI 复盘");
            }
        } catch (Exception e) {
            // 复盘失败不能影响"这局已经正常结束"这个事实
            log.warn("复盘环节出错（不影响对局结果）：{}", e.getMessage());
        }
    }

    /** 把复盘补写进战绩（落库时还没生成，所以是 UPDATE） */
    private void saveReflections(Long gameId, Map<Integer, String> reflections) {
        transactionTemplate.executeWithoutResult(status -> {
            for (Map.Entry<Integer, String> entry : reflections.entrySet()) {
                gameRecordMapper.update(null, new LambdaUpdateWrapper<GameRecord>()
                        .eq(GameRecord::getGameId, gameId)
                        .eq(GameRecord::getSeatNo, entry.getKey())
                        .set(GameRecord::getReflection, entry.getValue()));
            }
        });
    }

    /** 取这一局该用的脑子（开局时按房间选好了；查不到就退回默认，不让 AI 卡住） */
    private AiBrain brainFor(Long gameId) {
        AiBrain brain = gameBrains.get(gameId);
        return brain != null ? brain : aiBrainRouter.forRoom(null);
    }

    /** 服务关掉时把 AI 调度器一起收掉，别留守护线程 */
    @PreDestroy
    public void shutdownAiScheduler() {
        aiScheduler.shutdownNow();
    }

    // ============================================================
    // 工具
    // ============================================================

    private List<Player> listPlayers(Long roomId) {
        return playerMapper.selectList(new LambdaQueryWrapper<Player>()
                .eq(Player::getRoomId, roomId)
                .orderByAsc(Player::getSeatNo));
    }

    /**
     * 查这个房间以前打完的几局，拼成"人能读的摘要" —— 这是 AI 的<b>跨局记忆</b>。
     *
     * <p>只取最近 3 局，每局一行：几号位是什么身份、哪边赢了。
     * AI 拿到它就能形成"上一局 3 号是狼"这种印象；真人玩家本来就会这么记，
     * 让 AI 也有这份印象，一桌人的"关系"才能跨局延续。</p>
     */
    private List<String> loadRoomHistory(Long roomId) {
        List<Game> pastGames = gameMapper.selectList(new LambdaQueryWrapper<Game>()
                .eq(Game::getRoomId, roomId)
                .eq(Game::getStatus, GameStatus.FINISHED.name())
                .orderByDesc(Game::getId)
                .last("LIMIT 3"));
        if (pastGames.isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (Game past : pastGames) {
            List<GameRecord> records = gameRecordMapper.selectList(
                    new LambdaQueryWrapper<GameRecord>()
                            .eq(GameRecord::getGameId, past.getId())
                            .orderByAsc(GameRecord::getSeatNo));
            if (records.isEmpty()) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("第 ").append(past.getId()).append(" 局（")
                    .append("WOLF".equals(past.getWinnerCamp()) ? "狼人胜" : "好人胜")
                    .append("）：");
            for (GameRecord record : records) {
                sb.append(record.getSeatNo()).append(" 号=")
                        .append(roleLabel(record.getRoleCode())).append("　");
            }
            lines.add(sb.toString().trim());

            // 上一局各 AI 的复盘也带上 —— 这就是"AI 会进步"的来源：
            // 它下一局开口时，脑子里有自己的教训
            for (GameRecord record : records) {
                if (record.getReflection() != null && !record.getReflection().isBlank()) {
                    lines.add("　　" + (record.getAiName() == null ? "（AI）" : record.getAiName())
                            + " 上局的复盘：" + record.getReflection());
                }
            }
        }
        // 从旧到新，读起来是时间顺序
        Collections.reverse(lines);
        return lines;
    }

    /** 角色代码转成中文标签 */
    private String roleLabel(String roleCode) {
        if (roleCode == null) {
            return "未知";
        }
        for (Role role : Role.values()) {
            if (role.name().equals(roleCode)) {
                return role.getLabel();
            }
        }
        return roleCode;
    }

    private List<WerewolfPlayer> buildPlayers(List<Player> seats) {
        // AI 玩家没有 userId，查用户时要把它们排除掉（否则 IN (null) 会出问题）
        List<Long> userIds = new ArrayList<>();
        for (Player seat : seats) {
            if (seat.getUserId() != null) {
                userIds.add(seat.getUserId());
            }
        }

        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userMapper.selectBatchIds(userIds)) {
                userMap.put(user.getId(), user);
            }
        }

        List<WerewolfPlayer> players = new ArrayList<>();
        for (Player seat : seats) {
            String nickname;
            String avatar;
            if (seat.isAiPlayer()) {
                nickname = seat.getAiName();
                avatar = null;
            } else {
                User user = userMap.get(seat.getUserId());
                nickname = user == null ? ("玩家" + seat.getSeatNo()) : user.getNickname();
                avatar = user == null ? null : user.getAvatar();
            }
            WerewolfPlayer player = new WerewolfPlayer(seat.getSeatNo(), seat.getUserId(), nickname);
            player.setAvatar(avatar);
            player.setAi(seat.isAiPlayer());
            if (seat.isAiPlayer()) {
                // 人设要跟着进对局：规则 AI 靠它决定多激进，大模型靠它写提示词
                player.setPersona(AiPersona.byKey(seat.getAiPersona()));
            }
            players.add(player);
        }
        return players;
    }

    /** 判定某个阵营算赢还是输（平局时双方都记 DRAW） */
    private String judgeResult(String playerCamp, String winnerCamp) {
        if (WerewolfEngine.WINNER_DRAW.equals(winnerCamp)) {
            return WerewolfEngine.WINNER_DRAW;
        }
        return playerCamp.equals(winnerCamp) ? "WIN" : "LOSE";
    }

    private int seatOf(GameRuntime runtime, Long userId) {
        WerewolfState state = ((WerewolfEngine) runtime.getEngine()).getState();
        return state.getPlayers().stream()
                .filter(player -> userId != null && userId.equals(player.getUserId()))
                .map(WerewolfPlayer::getSeatNo)
                .findFirst()
                .orElseThrow(() -> new BusinessException("你不在这一局里"));
    }

    private Long userIdOfSeat(WerewolfState state, Integer seatNo) {
        if (seatNo == null) {
            return null;
        }
        WerewolfPlayer player = state.findBySeat(seatNo);
        return player == null ? null : player.getUserId();
    }

    /** 房间状态变了，通知房间频道刷新 */
    private void publishRoomChanged(Long roomId) {
        eventPublisher.publishEvent(new RoomChangedEvent(roomId, roomService.getRoom(String.valueOf(roomId))));
    }

    /** 一次行动要发的所有消息（在锁内准备好，锁外发出去） */
    private static class DispatchPlan {

        private ActionResult result;

        private final List<OutgoingMessage> publicMessages = new ArrayList<>();

        private final List<OutgoingMessage> privateMessages = new ArrayList<>();

        /** 这局是不是刚刚结束（决定要不要写战绩） */
        private boolean finished;
    }

    /** 一条待发送的消息 */
    private static class OutgoingMessage {

        /** null 表示广播到房间 */
        private final Long userId;

        private final WsMessageType type;

        private final Object payload;

        /** 给人看的提示文字，例如"天亮了，昨晚倒牌的是 3 号" */
        private final String message;

        private OutgoingMessage(Long userId, WsMessageType type, Object payload, String message) {
            this.userId = userId;
            this.type = type;
            this.payload = payload;
            this.message = message;
        }

        private static OutgoingMessage toRoom(WsMessageType type, Object payload, String message) {
            return new OutgoingMessage(null, type, payload, message);
        }

        private static OutgoingMessage toUser(Long userId, WsMessageType type, Object payload, String message) {
            return new OutgoingMessage(userId, type, payload, message);
        }
    }
}
