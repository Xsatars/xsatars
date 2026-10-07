package com.aitable.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket 推送的统一出口。
 *
 * <p>整个项目只从这里往外发消息，好处是消息格式不会各处走样。</p>
 */
@Component
public class WsEventPublisher {

    /** 房间公共频道前缀：/topic/room/{roomId} */
    private static final String ROOM_TOPIC_PREFIX = "/topic/room/";

    /** 私人频道后缀：/user/{userId}/queue/game */
    private static final String USER_QUEUE = "/queue/game";

    private final SimpMessagingTemplate messagingTemplate;

    public WsEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** 广播给订阅了这个房间的所有人（公开信息） */
    public void broadcastToRoom(Long roomId, WsMessageType type, Object data) {
        broadcastToRoom(roomId, type, data, null);
    }

    /** 广播，并带上一句给人看的提示（例如"天亮了，昨晚倒牌的是 3 号"） */
    public void broadcastToRoom(Long roomId, WsMessageType type, Object data, String message) {
        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomId, WsMessage.of(type, data, message));
    }

    /** 只发给某个人（私有信息，例如自己的角色、查验结果） */
    public void sendToUser(Long userId, WsMessageType type, Object data) {
        sendToUser(userId, type, data, null);
    }

    /** 只发给某个人，并带上一句给他看的提示（例如"查验结果：3 号是狼人"） */
    public void sendToUser(Long userId, WsMessageType type, Object data, String message) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), USER_QUEUE, WsMessage.of(type, data, message));
    }

    /** 给某个人回一条错误提示 */
    public void sendError(Long userId, String message) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), USER_QUEUE, WsMessage.error(message));
    }
}
