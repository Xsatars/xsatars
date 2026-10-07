package com.aitable.websocket;

import com.aitable.common.BusinessException;
import com.aitable.common.UserContext;
import com.aitable.game.core.ActionType;
import com.aitable.service.GameService;
import com.aitable.service.RoomService;
import com.aitable.vo.RoomVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket 消息总入口。
 *
 * <p>前端所有实时操作都发到 {@code /app/action}，格式见 {@link WsMessage}。
 * 这个类<b>只做分发</b>，不写任何业务规则：</p>
 * <ul>
 *   <li>房间相关 → {@link RoomService}</li>
 *   <li>游戏相关 → {@link GameService}（再往下就是 GameEngine）</li>
 * </ul>
 *
 * <p>以后接入 AI 玩家时，AI 会调用同一个 {@code GameService.submitAction}，
 * 通信层完全不用改。</p>
 */
@Controller
public class GameActionWsController {

    private static final Logger log = LoggerFactory.getLogger(GameActionWsController.class);

    private final RoomService roomService;
    private final GameService gameService;
    private final WsEventPublisher publisher;

    public GameActionWsController(RoomService roomService, GameService gameService, WsEventPublisher publisher) {
        this.roomService = roomService;
        this.gameService = gameService;
        this.publisher = publisher;
    }

    @MessageMapping("/action")
    public void handleAction(@Payload WsMessage message, Principal principal) {
        if (principal == null) {
            log.warn("收到消息但会话没有身份，忽略");
            return;
        }
        Long userId = Long.valueOf(principal.getName());

        WsMessageType type = WsMessageType.from(message == null ? null : message.getType());
        if (type == null) {
            publisher.sendError(userId, "未知的消息类型");
            return;
        }

        // WebSocket 线程里没有 HTTP 拦截器设置的用户上下文，这里手动设置，
        // 这样 Service / 引擎里都能统一用 UserContext.getUserId()。
        UserContext.setUserId(userId);
        try {
            switch (type) {
                case JOIN_ROOM -> handleJoinRoom(userId, message);
                case LEAVE_ROOM -> handleLeaveRoom(userId, message);
                case START_GAME -> handleStartGame(userId, message);
                case WEREWOLF_KILL, SEER_CHECK, WITCH_SAVE, WITCH_POISON, VOTE, SPEAK, PASS ->
                        handleGameAction(userId, type, message);
                default -> publisher.sendError(userId, "客户端不应该发送 " + type.name() + " 类型的消息");
            }
        } catch (BusinessException e) {
            // 业务上能预期到的错误（房间满、没轮到你、房间已经开了……）直接回给发起者
            publisher.sendError(userId, e.getMessage());
        } catch (Exception e) {
            log.error("处理 WebSocket 消息失败: type={}, userId={}", type, userId, e);
            publisher.sendError(userId, "服务器处理失败，请重试");
        } finally {
            UserContext.clear();
        }
    }

    /** 加入房间：data: { "roomNo": "123456" } */
    private void handleJoinRoom(Long userId, WsMessage message) {
        String roomNo = readString(message, "roomNo");
        RoomVO room = roomService.joinRoom(roomNo);
        // 房间频道的广播由 RoomService 发出的事件负责；
        // 发起者可能刚进来还没订阅，所以额外单独回执一份给他。
        publisher.sendToUser(userId, WsMessageType.ROOM_UPDATE, room);
    }

    /** 离开房间：data: { "roomId": 1, "playerId": 2 }，playerId 不传则按当前用户找 */
    private void handleLeaveRoom(Long userId, WsMessage message) {
        Long roomId = readLong(message, "roomId");
        Long playerId = readLong(message, "playerId");
        if (roomId == null) {
            publisher.sendError(userId, "缺少 roomId");
            return;
        }
        roomService.leaveRoom(roomId, playerId);
    }

    /** 房主开始游戏：data: { "roomId": 1 } */
    private void handleStartGame(Long userId, WsMessage message) {
        Long roomId = readLong(message, "roomId");
        if (roomId == null) {
            publisher.sendError(userId, "缺少 roomId");
            return;
        }
        gameService.startGame(roomId);
        // 开局后的发牌、阶段变化都由 GameService 通过事件推给所有人
    }

    /** 游戏行动：data: { "gameId": 1, "targetSeat": 3, "text": "..." } */
    private void handleGameAction(Long userId, WsMessageType type, WsMessage message) {
        Long gameId = readLong(message, "gameId");
        if (gameId == null) {
            publisher.sendError(userId, "缺少 gameId");
            return;
        }
        Integer targetSeat = readInt(message, "targetSeat");
        String text = readString(message, "text");
        gameService.submitAction(gameId, userId, ActionType.valueOf(type.name()), targetSeat, text);
    }

    // ---------- 取值工具 ----------

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(WsMessage message) {
        if (message != null && message.getData() instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String readString(WsMessage message, String key) {
        Object value = dataOf(message).get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long readLong(WsMessage message, String key) {
        Object value = dataOf(message).get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer readInt(WsMessage message, String key) {
        Long value = readLong(message, key);
        return value == null ? null : value.intValue();
    }
}
