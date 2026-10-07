package com.aitable.game.core;

import java.util.List;

/**
 * 游戏引擎接口。
 *
 * <p><b>引擎是唯一有权改变游戏状态的地方。</b>
 * Controller、WebSocket、数据库都不许直接改状态，只能通过下面的 {@link #apply} 提交行动。</p>
 *
 * <p>V0.1 只有狼人杀一个实现（{@code com.aitable.game.werewolf.WerewolfEngine}）。
 * 以后加德州扑克、剧本杀，就是再写一个实现类，其它模块不用改。</p>
 */
public interface GameEngine {

    /** 游戏类型，例如 WEREWOLF */
    String gameType();

    /**
     * 开始游戏：分配角色、把阶段推进到第一夜。
     *
     * @return 开局要广播的事件（每个人自己的角色会以"私有事件"的形式返回）
     */
    List<GameEvent> start();

    /**
     * 提交一次行动。
     *
     * <p>引擎会先把所有合法性都检查一遍（是不是你的回合、你的角色能不能做这个动作、
     * 目标合不合法），全部通过之后才改状态。不合法时返回失败，状态一点都不会动。</p>
     */
    ActionResult apply(ActionRequest request);

    /** 取某个座位的完整视图（公共 + 他自己的私有信息） */
    GameView viewFor(int seatNo);

    /** 公共状态（房间里所有人都一样的那部分） */
    PublicGameState publicState();

    /** 某个座位此刻能做的行动 */
    List<String> optionsFor(int seatNo);

    /** 游戏是否已结束 */
    boolean isFinished();

    /** 当前阶段 */
    GamePhase phase();

    /** 胜方阵营（未结束时返回 null） */
    String winnerCamp();
}
