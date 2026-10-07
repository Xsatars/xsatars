package com.aitable.websocket;

/**
 * WebSocket 消息的统一格式（收和发都是它）。
 *
 * <pre>
 * 客户端发：
 * { "type": "JOIN_ROOM", "data": { "roomNo": "123456" } }
 *
 * 服务端发：
 * { "type": "ROOM_UPDATE", "data": { ...房间信息... }, "timestamp": 1791352266769 }
 * 服务端报错：
 * { "type": "ERROR", "message": "房间已满", "timestamp": ... }
 * </pre>
 *
 * <p>所有游戏动作也都走这一个格式，好处是以后 AI 玩家接进来时，
 * 用的是一模一样的"行动请求"结构。</p>
 */
public class WsMessage {

    /** 消息类型，见 {@link WsMessageType} */
    private String type;

    /** 消息内容，不同类型结构不同 */
    private Object data;

    /** 只有 ERROR 类型才用：给人看的错误说明 */
    private String message;

    /** 服务端发出时填的时间戳 */
    private Long timestamp;

    public WsMessage() {
    }

    public WsMessage(String type, Object data, String message) {
        this.type = type;
        this.data = data;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public static WsMessage of(WsMessageType type, Object data) {
        return new WsMessage(type.name(), data, null);
    }

    /** 带一句给人看的提示（例如"天亮了，昨晚倒牌的是 3 号"） */
    public static WsMessage of(WsMessageType type, Object data, String message) {
        return new WsMessage(type.name(), data, message);
    }

    public static WsMessage error(String message) {
        return new WsMessage(WsMessageType.ERROR.name(), null, message);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
