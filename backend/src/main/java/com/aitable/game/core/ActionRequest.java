package com.aitable.game.core;

/**
 * 一次"行动请求"。
 *
 * <p><b>这是整个项目最重要的一个约定</b>：不管行动是人点的按钮，
 * 还是以后 AI 玩家决定的，都必须变成这个对象，然后交给 GameEngine 处理。</p>
 *
 * <p>为什么这么做：</p>
 * <ul>
 *   <li>人类玩家和 AI 玩家走<b>完全相同的入口</b>，AI 接进来时引擎一行都不用改</li>
 *   <li>请求只是"我想这么做"，<b>合不合法由引擎说了算</b>——
 *       AI 没法绕过规则直接改游戏状态</li>
 * </ul>
 */
public class ActionRequest {

    /** 发起行动的人坐在几号位 */
    private int seatNo;

    /** 想做什么 */
    private ActionType type;

    /** 目标座位号（刀人、查验、投票、毒人时需要） */
    private Integer targetSeat;

    /** 发言内容（SPEAK 时用） */
    private String text;

    public ActionRequest() {
    }

    public ActionRequest(int seatNo, ActionType type, Integer targetSeat) {
        this.seatNo = seatNo;
        this.type = type;
        this.targetSeat = targetSeat;
    }

    public static ActionRequest of(int seatNo, ActionType type, Integer targetSeat) {
        return new ActionRequest(seatNo, type, targetSeat);
    }

    public static ActionRequest speak(int seatNo, String text) {
        ActionRequest request = new ActionRequest(seatNo, ActionType.SPEAK, null);
        request.setText(text);
        return request;
    }

    public int getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(int seatNo) {
        this.seatNo = seatNo;
    }

    public ActionType getType() {
        return type;
    }

    public void setType(ActionType type) {
        this.type = type;
    }

    public Integer getTargetSeat() {
        return targetSeat;
    }

    public void setTargetSeat(Integer targetSeat) {
        this.targetSeat = targetSeat;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
