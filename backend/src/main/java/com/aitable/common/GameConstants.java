package com.aitable.common;

/**
 * 游戏相关常量。
 *
 * <p>V0.1 只有一种游戏、一个板子，所以先写死成常量；
 * 以后支持多个板子时，这些值会从配置/数据库里读。</p>
 */
public final class GameConstants {

    private GameConstants() {
    }

    /** 游戏类型：狼人杀 */
    public static final String GAME_TYPE_WEREWOLF = "WEREWOLF";

    /** 板子代号：狼人杀 6 人局 */
    public static final String BOARD_WEREWOLF_6 = "WEREWOLF_6";

    /** 6 人局人数：2 狼 + 1 预言家 + 1 女巫 + 2 平民 */
    public static final int WEREWOLF_6_PLAYER_COUNT = 6;

    /** 6 人局各角色数量 */
    public static final int WEREWOLF_6_WOLF_COUNT = 2;
    public static final int WEREWOLF_6_SEER_COUNT = 1;
    public static final int WEREWOLF_6_WITCH_COUNT = 1;
    public static final int WEREWOLF_6_VILLAGER_COUNT = 2;

    /** 房间号位数 */
    public static final int ROOM_NO_LENGTH = 6;
}
