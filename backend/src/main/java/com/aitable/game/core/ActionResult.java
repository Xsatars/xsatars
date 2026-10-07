package com.aitable.game.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 行动的处理结果。
 *
 * <p>注意：<b>行动不合法时只是返回失败，绝不产生任何状态变更</b>。
 * 引擎先把所有校验做完，再动状态。</p>
 */
public class ActionResult {

    private boolean success;

    /** 给人看的结果说明；失败时就是失败原因 */
    private String message;

    /** 这次行动产生的游戏事件（引擎已经产好，交给广播层按可见性投递） */
    private List<GameEvent> events = new ArrayList<>();

    public static ActionResult ok(String message) {
        ActionResult result = new ActionResult();
        result.success = true;
        result.message = message;
        return result;
    }

    public static ActionResult ok(String message, List<GameEvent> events) {
        ActionResult result = ok(message);
        if (events != null) {
            result.events = events;
        }
        return result;
    }

    public static ActionResult fail(String message) {
        ActionResult result = new ActionResult();
        result.success = false;
        result.message = message;
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<GameEvent> getEvents() {
        return events;
    }

    public void setEvents(List<GameEvent> events) {
        this.events = events;
    }

    public void addEvent(GameEvent event) {
        this.events.add(event);
    }
}
