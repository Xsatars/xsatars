package com.aitable.websocket;

/**
 * WebSocket 消息类型。
 *
 * <p>命名沿用项目最初的需求文档。带「Step 5/6」注释的类型会在后面的步骤里实现，
 * 现在先定义好，前端可以照着这份协议写。</p>
 */
public enum WsMessageType {

    // ==================== 客户端 → 服务端 ====================

    /** 加入房间。data: { "roomNo": "123456" } */
    JOIN_ROOM,

    /** 离开房间。data: { "roomId": 1, "playerId": 2 } */
    LEAVE_ROOM,

    /** 房主开始游戏。data: { "roomId": 1 }（Step 5-6 实现） */
    START_GAME,

    /** 发言。data: { "text": "我觉得 3 号是狼" }（Step 6 实现） */
    SPEAK,

    /** 狼人刀人。data: { "targetSeat": 4 }（Step 6 实现） */
    WEREWOLF_KILL,

    /** 预言家查验。data: { "targetSeat": 5 }（Step 6 实现） */
    SEER_CHECK,

    /** 女巫用解药。data: {} （Step 6 实现） */
    WITCH_SAVE,

    /** 女巫用毒药。data: { "targetSeat": 6 }（Step 6 实现） */
    WITCH_POISON,

    /** 投票。data: { "gameId": 1, "targetSeat": 2 } */
    VOTE,

    /** 放弃本次操作，目前用于女巫"今晚不用药"。data: { "gameId": 1 } */
    PASS,

    // ==================== 服务端 → 客户端 ====================

    /** 房间状态更新（座位变化、状态变化） */
    ROOM_UPDATE,

    /** 游戏开始（Step 5-6） */
    GAME_START,

    /** 分配角色，只发给本人（Step 5-6） */
    ROLE_ASSIGN,

    /** 阶段变化：夜晚 / 白天 / 发言 / 投票（Step 5-6） */
    PHASE_CHANGE,

    /** 有人发言（Step 6） */
    PLAYER_SPEAK,

    /** 夜晚结果，例如谁死了（Step 6） */
    NIGHT_RESULT,

    /** 有人死亡（Step 6） */
    PLAYER_DEAD,

    /** 投票进展（Step 6） */
    VOTE_UPDATE,

    /** 投票结果（Step 6） */
    VOTE_RESULT,

    /** 游戏状态同步（Step 6） */
    GAME_STATE_UPDATE,

    /** 游戏结束（Step 5-6） */
    GAME_END,

    /** 错误提示，只发给触发者 */
    ERROR,

    /**
     * 你的账号在另一个页面登录了，当前页面作废。
     *
     * <p>同一个账号只允许一个页面在玩：后来者会顶替先来者，旧页面收到这条消息后
     * 自动断开并回到登录页。</p>
     */
    SESSION_REPLACED;

    /** 把收到的字符串转成枚举，不认识就返回 null */
    public static WsMessageType from(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        for (WsMessageType value : values()) {
            if (value.name().equalsIgnoreCase(type.trim())) {
                return value;
            }
        }
        return null;
    }
}
