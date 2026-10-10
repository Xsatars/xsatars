package com.aitable.game.ai;

import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.GameView;

/**
 * AI 的"脑子"：拿到自己当前能看到的东西，决定下一步做什么。
 *
 * <p><b>它只能返回一个动作请求</b>，不能直接改游戏状态 ——
 * AI 和真人走的是完全相同的入口（{@code GameService.submitAction}），
 * 所以规则校验对 AI 一样有效，AI 做不出"开挂"的事。</p>
 *
 * <p>这个接口有两套实现：</p>
 * <ul>
 *   <li>{@link SimpleAiBrain} —— 纯规则，不联网，一个人也能开局</li>
 *   <li>以后接大模型的那套（V0.2 第 3 步）—— 只换实现，外面的代码一行不用改</li>
 * </ul>
 */
public interface AiBrain {

    /** 这个脑子的名字（日志里用） */
    String name();

    /**
     * 决定下一步做什么。
     *
     * @param view 这个 AI 能看到的一切（公共状态 + 它自己的私人状态）
     * @return 想执行的动作；返回 {@code null} 表示"这一步我没啥要做的"
     */
    ActionRequest decide(GameView view);
}
