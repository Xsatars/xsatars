package com.aitable.game.core;

/**
 * 游戏事件：引擎里发生的一件事。
 *
 * <p>每个事件都带"可见性"，广播层据此决定发给谁——这是信息隔离的关键：</p>
 * <ul>
 *   <li>{@link #publicEvent} = true：所有人都能看（谁死了、阶段变了、投票结果）</li>
 *   <li>{@link #publicEvent} = false：只有 {@link #ownerSeat} 那个人能看
 *       （例如"你查验出 3 号是狼人"）</li>
 * </ul>
 */
public class GameEvent {

    /** 事件类型，名字和 WebSocket 消息类型一致，例如 PHASE_CHANGE、PLAYER_DEAD */
    private String type;

    /** true=广播给房间所有人；false=只发给 ownerSeat */
    private boolean publicEvent;

    /** 私有事件属于哪个座位 */
    private Integer ownerSeat;

    /** 事件内容，会原样发给前端 */
    private Object payload;

    /** 给人看的文字说明（例如"天亮了，昨晚是平安夜"） */
    private String message;

    public GameEvent() {
    }

    /** 公开事件 */
    public static GameEvent publicEvent(String type, Object payload, String message) {
        GameEvent event = new GameEvent();
        event.type = type;
        event.publicEvent = true;
        event.payload = payload;
        event.message = message;
        return event;
    }

    /** 私有事件（只给某个座位看） */
    public static GameEvent privateEvent(String type, int ownerSeat, Object payload, String message) {
        GameEvent event = new GameEvent();
        event.type = type;
        event.publicEvent = false;
        event.ownerSeat = ownerSeat;
        event.payload = payload;
        event.message = message;
        return event;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isPublicEvent() {
        return publicEvent;
    }

    public void setPublicEvent(boolean publicEvent) {
        this.publicEvent = publicEvent;
    }

    public Integer getOwnerSeat() {
        return ownerSeat;
    }

    public void setOwnerSeat(Integer ownerSeat) {
        this.ownerSeat = ownerSeat;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
