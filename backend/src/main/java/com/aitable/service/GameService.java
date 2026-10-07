package com.aitable.service;

import com.aitable.game.core.ActionType;
import com.aitable.game.core.GameView;

/**
 * 游戏服务：把"引擎"和"房间/数据库/消息"这些外部世界连起来。
 *
 * <p>它自己不判断任何游戏规则——规则都在 GameEngine 里。
 * 这里只做三件事：</p>
 * <ol>
 *   <li>开局：校验房间条件 → 建对局 → 让引擎发牌 → 把事件投递出去</li>
 *   <li>行动：把请求交给引擎 → 引擎说成功就广播，说失败就回给发起者</li>
 *   <li>结束：把战绩写进数据库</li>
 * </ol>
 */
public interface GameService {

    /**
     * 开始游戏（只有房主能发起，且必须满员）。
     *
     * @return 新对局的 ID
     */
    Long startGame(Long roomId);

    /**
     * 提交一次行动。
     *
     * <p><b>人类玩家现在走这里，以后的 AI 玩家也走这里</b>——
     * 引擎那层完全不需要知道是谁在操作。</p>
     */
    void submitAction(Long gameId, Long userId, ActionType type, Integer targetSeat, String text);

    /** 取某个人在某局里的视图（刷新页面/断线重连时用） */
    GameView viewFor(Long gameId, Long userId);

    /**
     * 检查并处理超时。
     *
     * <p>如果这一局卡在某一步超过了配置的时长（有人挂机），就替他做默认处理
     * （夜晚空刀 / 发言跳过 / 投票弃权），让对局继续走下去。</p>
     *
     * @return 是否真的处理了（没超时、或对局已不在内存里都返回 false）
     */
    boolean handleTimeoutIfNeeded(Long gameId);

    /**
     * 查我当前是否有还没结束的对局（没有返回 null）。
     *
     * <p>用于"中途退出后重新连接"：玩家关掉页面再回来，靠它找回自己那一局。</p>
     */
    GameView currentGameFor(Long userId);

    /** 玩家连上了：标记为在线 */
    void markOnline(Long userId);

    /**
     * 玩家掉线了：标记为离线，并且<b>立刻</b>替他跳过当前该他做的操作，
     * 不用让全桌干等超时。
     */
    void markOffline(Long userId);

    /**
     * 强制终止一局对局（管理员关房间时用）。
     *
     * <p>会把对局记录标成已结束、从内存里移除，并广播一条"已被管理员终止"。</p>
     */
    void forceCloseGame(Long gameId);
}
