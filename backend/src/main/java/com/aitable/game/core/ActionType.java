package com.aitable.game.core;

/**
 * 玩家能提交的行动类型。
 *
 * <p>这些名字和 WebSocket 消息类型一一对应，所以前端/以后的 AI 玩家
 * 用的是同一套"行动"概念。</p>
 */
public enum ActionType {

    /** 狼人选择击杀目标（夜晚） */
    WEREWOLF_KILL,

    /** 预言家查验某人（夜晚） */
    SEER_CHECK,

    /** 女巫使用解药（夜晚） */
    WITCH_SAVE,

    /** 女巫使用毒药（夜晚） */
    WITCH_POISON,

    /** 白天投票放逐某人 */
    VOTE,

    /**
     * 猎人开枪带走一个人（只有猎人、且只在 {@link GamePhase#HUNTER_SHOOT} 阶段可用）。
     *
     * <p>不想开枪就交 {@link #PASS}。</p>
     */
    HUNTER_SHOOT,

    /** 发言 */
    SPEAK,

    /** 什么都不做（过） */
    PASS
}
