package com.aitable.game.ai;

/**
 * 局后复盘。
 *
 * <p>一局打完，每个 AI 回头看看自己这一局 —— 哪里判断错了、哪一步赌对了。
 * 复盘会记进房间记忆，<b>下一局它会带着这个教训上桌</b>，
 * 这是"AI 有人味"再往下一层：不只是记得别人，还会反思自己。</p>
 */
public interface AiReflector {

    /** 给个默认实现，让它成为函数式接口 */
    default String name() {
        return "复盘";
    }

    /**
     * 复盘自己这一局。
     *
     * @return 一句话（第一人称，符合人设）；返回 {@code null} 表示这次不总结
     */
    String reflect(ReflectContext context);
}
