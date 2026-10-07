package com.aitable.game.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 私人状态：<b>只有这个座位自己能拿到的东西</b>。
 *
 * <p>我的身份、我的狼队友、我查出来谁是狼、我药水还剩几瓶、我现在能做什么。</p>
 *
 * <p>这就是"不能把完整 GameState 直接发给所有人"的落地点：
 * 引擎只把 {@link PlayerPrivateState} 发给对应的人，别人的身份根本不会出现在他的数据里。</p>
 */
public class PlayerPrivateState {

    /** 我的座位号 */
    private int mySeat;

    private boolean alive;

    /** 我的角色：WOLF / SEER / WITCH / VILLAGER */
    private String myRole;

    /** 我的角色中文名：狼人 / 预言家 / 女巫 / 平民 */
    private String myRoleLabel;

    /** 我的阵营：WOLF / GOOD */
    private String myCamp;

    /** 狼队友的座位号（只有狼人有值） */
    private List<Integer> teammateSeats = new ArrayList<>();

    /** 我此刻可以做的行动（前端据此显示哪些按钮能点） */
    private List<String> availableActions = new ArrayList<>();

    /** 给玩家的操作提示，例如"请选择要击杀的玩家" */
    private String hint;

    /**
     * 其它私人信息，按角色不同内容不同，例如：
     * <ul>
     *   <li>预言家：{@code seerResults = {3: true}} 表示查过 3 号是狼</li>
     *   <li>女巫：{@code witchSaveUsed / witchPoisonUsed}、{@code wolfTargetSeat}（今晚谁被刀）</li>
     *   <li>狼人：{@code wolfVotes} 队友们的刀人选择</li>
     * </ul>
     */
    private Map<String, Object> info = new HashMap<>();

    public int getMySeat() {
        return mySeat;
    }

    public void setMySeat(int mySeat) {
        this.mySeat = mySeat;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public String getMyRole() {
        return myRole;
    }

    public void setMyRole(String myRole) {
        this.myRole = myRole;
    }

    public String getMyRoleLabel() {
        return myRoleLabel;
    }

    public void setMyRoleLabel(String myRoleLabel) {
        this.myRoleLabel = myRoleLabel;
    }

    public String getMyCamp() {
        return myCamp;
    }

    public void setMyCamp(String myCamp) {
        this.myCamp = myCamp;
    }

    public List<Integer> getTeammateSeats() {
        return teammateSeats;
    }

    public void setTeammateSeats(List<Integer> teammateSeats) {
        this.teammateSeats = teammateSeats;
    }

    public List<String> getAvailableActions() {
        return availableActions;
    }

    public void setAvailableActions(List<String> availableActions) {
        this.availableActions = availableActions;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public Map<String, Object> getInfo() {
        return info;
    }

    public void setInfo(Map<String, Object> info) {
        this.info = info;
    }

    public void putInfo(String key, Object value) {
        this.info.put(key, value);
    }
}
