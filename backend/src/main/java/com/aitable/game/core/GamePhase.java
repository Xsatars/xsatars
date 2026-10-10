package com.aitable.game.core;

/**
 * 游戏阶段。
 *
 * <p>V0.1 是狼人杀，阶段顺序是这样走的：</p>
 * <pre>
 * WAITING（房间里等人）
 *   → NIGHT（夜晚：狼人刀人 → 预言家查验 → 女巫用药）
 *   → DAY（白天：公布昨晚死讯）
 *   → DAY_DISCUSSION（玩家依次发言）
 *   → DAY_VOTE（所有人投票放逐）
 *   → 判定胜负；没结束就回到 NIGHT，开始下一轮
 *   → FINISHED（有人赢了）
 * </pre>
 *
 * <p>谁能行动、能做什么动作，全部由当前阶段决定——
 * UI 的按钮和 AI 的可用动作都从这里推导，不需要另写一套。</p>
 */
public enum GamePhase {

    /** 等待房主开始游戏 */
    WAITING("等待开始"),

    /** 夜晚：狼人 / 预言家 / 女巫依次行动 */
    NIGHT("夜晚"),

    /** 白天：公布昨晚的死亡结果 */
    DAY("白天·公布死讯"),

    /** 白天：存活玩家依次发言 */
    DAY_DISCUSSION("白天·发言"),

    /** 白天：全体存活玩家投票放逐 */
    DAY_VOTE("白天·投票"),

    /**
     * 猎人开枪（8 人局才有）。
     *
     * <p>它不是流程里一个"固定时刻"，而是<b>插播</b>：猎人被狼刀死、
     * 或被投票放逐时，流程会在这里暂停一下等他开枪，开完再回到原来的位置继续。</p>
     *
     * <p>注意被女巫<b>毒死</b>的猎人<b>不能</b>开枪 —— 这是标准规则，
     * 也是狼队用毒药反制猎人的办法。</p>
     */
    HUNTER_SHOOT("猎人开枪"),

    /** 游戏结束 */
    FINISHED("游戏结束");

    private final String label;

    GamePhase(String label) {
        this.label = label;
    }

    /** 中文名，给前端显示 */
    public String getLabel() {
        return label;
    }
}
