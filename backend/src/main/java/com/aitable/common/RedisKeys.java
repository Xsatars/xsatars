package com.aitable.common;

/**
 * Redis key 统一在这里定义，避免各个类里到处拼字符串、拼错还不好查。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 登录 token → 用户ID。例：ai-table:token:8f3a... */
    public static final String TOKEN_PREFIX = "ai-table:token:";

    /** 在线用户 → 最后活跃时间（WebSocket 阶段使用） */
    public static final String ONLINE_PREFIX = "ai-table:online:";

    /** 房间从什么时候开始"全员离线"。例：ai-table:room-offline:12 */
    public static final String ROOM_OFFLINE_PREFIX = "ai-table:room-offline:";

    public static String token(String token) {
        return TOKEN_PREFIX + token;
    }

    public static String online(Long userId) {
        return ONLINE_PREFIX + userId;
    }

    public static String roomOffline(Long roomId) {
        return ROOM_OFFLINE_PREFIX + roomId;
    }
}
