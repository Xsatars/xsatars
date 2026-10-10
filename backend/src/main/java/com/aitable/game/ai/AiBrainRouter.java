package com.aitable.game.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 按房间选的"脑子"分发：这局用规则 AI 还是大模型 AI。
 *
 * <p>为什么需要它：以前整个服务只装<b>一套</b> AI（配置 {@code ai-table.ai.brain} 写死），
 * 想换成另一种得改配置重启。现在两个都装上，<b>由房间自己选</b> ——
 * 玩家想省流量、想秒开一局就用规则 AI；想要有人味的发言就切大模型。</p>
 *
 * <p><b>为什么只切 AI 玩家</b>：主持人和局后复盘不属于"玩家"，它们是房间氛围的一部分，
 * 继续跟全局配置走。否则一个房间切了规则 AI，连主持人都不说话了，玩家会以为坏了。</p>
 */
public class AiBrainRouter {

    private static final Logger log = LoggerFactory.getLogger(AiBrainRouter.class);

    /** 规则 AI：不联网、免费、永远可用，也是兜底 */
    private final AiBrain ruleBrain;

    /** 大模型 AI；没配 Key 时为 null */
    private final AiBrain llmBrain;

    public AiBrainRouter(AiBrain ruleBrain, AiBrain llmBrain) {
        this.ruleBrain = ruleBrain;
        this.llmBrain = llmBrain;
    }

    /** 大模型这档现在能不能用（前端据此决定要不要禁用选项） */
    public boolean isLlmAvailable() {
        return llmBrain != null;
    }

    /**
     * 这个房间该用哪个脑子。
     *
     * <p>选了大模型但服务端没配 Key 时，<b>安静地退回规则 AI</b> ——
     * 一桌人正在等着，不能因为一个配置问题就卡住。</p>
     */
    public AiBrain forRoom(String brainType) {
        if ("llm".equalsIgnoreCase(brainType)) {
            if (llmBrain != null) {
                return llmBrain;
            }
            log.debug("房间选了大模型 AI，但服务端没配 Key，退回规则 AI");
        }
        return ruleBrain;
    }
}
