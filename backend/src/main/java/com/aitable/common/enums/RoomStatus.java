package com.aitable.common.enums;

/**
 * 房间状态。
 *
 * <p>数据库里 rooms.status 存的是字符串，取值就是这里的 {@link #name()}。</p>
 */
public enum RoomStatus {

    /** 等待玩家加入 / 等待开局 */
    WAITING,

    /** 游戏进行中 */
    PLAYING,

    /** 游戏已结束 */
    FINISHED
}
