package com.aitable.game.ai;

/**
 * 主持人（上帝）解说。
 *
 * <p>他负责把干巴巴的规则播报讲得有画面感：</p>
 * <ul>
 *   <li>平铺直叙：「昨晚倒牌的是 3 号」</li>
 *   <li>主持人说：「昨晚倒下的是 3 号 —— 他睡前还说自己是好人，可惜了。」</li>
 * </ul>
 *
 * <p><b>硬约束</b>：主持人说的话全场都能听到，所以<b>绝不能泄露任何人的身份</b>
 * （除了游戏结束那一句，那时候身份本来就公开了）。给他的素材只有公开信息。</p>
 */
public interface HostNarrator {

    /**
     * 这套解说的名字（日志里用）。
     *
     * <p>给个默认实现是为了让它成为<b>函数式接口</b> ——
     * "不接大模型"的那种实现直接写成 {@code context -> null} 就行。</p>
     */
    default String name() {
        return "主持人";
    }

    /**
     * 讲一句。
     *
     * @return 解说词；返回 {@code null} 或空串就用 {@link HostContext#getFallbackText()}，
     *         保证主持人永远不会沉默
     */
    String narrate(HostContext context);
}
