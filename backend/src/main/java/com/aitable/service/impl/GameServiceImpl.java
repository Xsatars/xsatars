package com.aitable.service.impl;

import com.aitable.common.BusinessException;
import com.aitable.common.UserContext;
import com.aitable.common.enums.GameStatus;
import com.aitable.common.enums.RoomStatus;
import com.aitable.common.event.RoomChangedEvent;
import com.aitable.entity.Game;
import com.aitable.entity.GameRecord;
import com.aitable.entity.Player;
import com.aitable.entity.Room;
import com.aitable.entity.User;
import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionResult;
import com.aitable.game.core.ActionType;
import com.aitable.game.core.GameEvent;
import com.aitable.game.core.GameManager;
import com.aitable.game.core.GamePhase;
import com.aitable.game.core.GameRuntime;
import com.aitable.game.core.GameView;
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
import com.aitable.websocket.WsEventPublisher;
import com.aitable.websocket.WsMessageType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** 每个需要玩家操作的步骤最多等多少秒（超时自动跳过） */
    @Value("${ai-table.game.action-timeout-seconds:90}")
    private int actionTimeoutSeconds;

    public GameServiceImpl(RoomMapper roomMapper, PlayerMapper playerMapper, UserMapper userMapper,
                           GameMapper gameMapper, GameRecordMapper gameRecordMapper,
                           RoomService roomService, GameManager gameManager,
                           WsEventPublisher publisher, ApplicationEventPublisher eventPublisher,
                           TransactionTemplate transactionTemplate) {
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

        // 组装引擎
        WerewolfEngine engine = WerewolfEngine.create(game.getId(), roomId, buildPlayers(seats), actionTimeoutSeconds);
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
            boolean inThisGame = state.getPlayers().stream()
                    .anyMatch(player -> player.getUserId().equals(userId));
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
        return state.getPlayers().stream()
                .filter(player -> player.getUserId().equals(userId))
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

        for (WerewolfPlayer player : state.getPlayers()) {
            plan.privateMessages.add(OutgoingMessage.toUser(player.getUserId(),
                    WsMessageType.GAME_STATE_UPDATE, engine.viewFor(player.getSeatNo()), null));
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
                record.setSeatNo(player.getSeatNo());
                record.setRoleCode(player.getRole().name());
                record.setCamp(player.getRole().getCamp().name());
                record.setResult(judgeResult(player.getRole().getCamp().name(), state.getWinnerCamp()));
                record.setSurvived(player.isAlive() ? 1 : 0);
                gameRecordMapper.insert(record);
            }

            // 房间回到等待状态，可以再来一局
            Room room = roomMapper.selectById(runtime.getRoomId());
            if (room != null) {
                room.setStatus(RoomStatus.WAITING.name());
                room.setCurrentGameId(null);
                roomMapper.updateById(room);

                // 新一局所有人都要重新准备（房主本身不需要准备，保持已就绪）
                List<Player> seats = playerMapper.selectList(
                        new LambdaQueryWrapper<Player>().eq(Player::getRoomId, room.getId()));
                for (Player seat : seats) {
                    boolean isOwner = seat.getUserId().equals(room.getOwnerUserId());
                    seat.setReady(isOwner ? 1 : 0);
                    playerMapper.updateById(seat);
                }
            }
        });

        publishRoomChanged(runtime.getRoomId());
        gameManager.remove(runtime.getGameId());
        log.info("对局 {} 已结束并写入战绩（{} 轮，{} 获胜）",
                runtime.getGameId(), state.getRound(), state.getWinnerCamp());
    }

    // ============================================================
    // 工具
    // ============================================================

    private List<Player> listPlayers(Long roomId) {
        return playerMapper.selectList(new LambdaQueryWrapper<Player>()
                .eq(Player::getRoomId, roomId)
                .orderByAsc(Player::getSeatNo));
    }

    private List<WerewolfPlayer> buildPlayers(List<Player> seats) {
        List<Long> userIds = new ArrayList<>();
        seats.forEach(seat -> userIds.add(seat.getUserId()));

        Map<Long, User> userMap = new HashMap<>();
        for (User user : userMapper.selectBatchIds(userIds)) {
            userMap.put(user.getId(), user);
        }

        List<WerewolfPlayer> players = new ArrayList<>();
        for (Player seat : seats) {
            User user = userMap.get(seat.getUserId());
            String nickname = user == null ? ("玩家" + seat.getSeatNo()) : user.getNickname();
            WerewolfPlayer player = new WerewolfPlayer(seat.getSeatNo(), seat.getUserId(), nickname);
            player.setAvatar(user == null ? null : user.getAvatar());
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

    private int seatOf(GameRuntime runtime, Long userId) {        WerewolfState state = ((WerewolfEngine) runtime.getEngine()).getState();
        return state.getPlayers().stream()
                .filter(player -> player.getUserId().equals(userId))
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
