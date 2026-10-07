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
