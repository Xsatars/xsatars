package com.aitable.game.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用大模型做解说的主持人。
 *
 * <p>输入只有"已经公开的信息"，所以它说不出泄密的话 —— 这是设计上的保险，
 * 不是靠提示词求它别说。游戏结束那一句例外，那时候身份本来就公开了。</p>
 *
 * <p>调不通就退回 {@link HostContext#getFallbackText()}，
 * 主持人<b>永远不会沉默</b>。</p>
 */
public class LlmHostNarrator implements HostNarrator {

    private static final Logger log = LoggerFactory.getLogger(LlmHostNarrator.class);

    private final DeepSeekClient client;

    public LlmHostNarrator(DeepSeekClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "大模型主持人";
    }

    @Override
    public String narrate(HostContext context) {
        String reply = client.chatText(systemPrompt(context), userPrompt(context));
        if (reply == null || reply.isBlank()) {
            return context.getFallbackText();
        }
        String text = clean(reply, context);
        return text.isBlank() ? context.getFallbackText() : text;
    }

    /** 清一下模型的小毛病：引号、换行、太长 */
    private String clean(String reply, HostContext context) {
        String text = reply.trim();
        // 有时候它会把整句话套在引号里
        if (text.length() > 1 && (text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("「") && text.endsWith("」"))) {
            text = text.substring(1, text.length() - 1).trim();
        }
        // 只要第一行，别让它在解说里写小作文
        int nl = text.indexOf('\n');
        if (nl > 0) {
            text = text.substring(0, nl).trim();
        }
        if (text.length() > 120) {
            text = text.substring(0, 120);
        }
        if (text.isBlank()) {
            log.debug("主持人这次没说出话（场合 {}），用兜底文案", context.getMoment());
        }
        return text;
    }

    private String systemPrompt(HostContext context) {
        boolean gameOver = "GAME_END".equals(context.getMoment());
        String secretRule = gameOver
                ? "- 游戏已经结束了，**这一句你可以点出身份**，把真相说出来更有味道"
                : """
                  - **绝对不能说出任何人的身份**（谁是狼、谁是预言家、谁是女巫）
                  - 也不能暗示"某人可能是狼"、不能暗示谁对谁错
                  - 你只能讲已经公开的事实：谁出局了、还剩几个人""";

        return """
                你是一局 6 人狼人杀的**主持人（上帝）**，负责在关键节点说一句话。

                说话风格：
                - 就一句话，25~50 字，别写成一段
                - 有画面感、带点戏剧性，像真人主持人在念白
                - 可以用"各位""看来""可惜""有意思"这类语气词
                - 不要用 emoji，不要用引号把整句包起来

                %s

                只输出这一句话本身，不要解释、不要加前缀。
                """.formatted(secretRule);
    }

    private String userPrompt(HostContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("【场合】").append(momentLabel(context.getMoment())).append('\n');
        sb.append("【第几轮】第 ").append(context.getRound()).append(" 轮\n");
        if (context.getPhaseLabel() != null) {
            sb.append("【阶段】").append(context.getPhaseLabel()).append('\n');
        }

        if (!context.getDeaths().isEmpty()) {
            sb.append("【昨夜出局】").append(String.join("、", context.getDeaths())).append('\n');
        } else if ("DAWN".equals(context.getMoment())) {
            sb.append("【昨夜出局】没有人出局（平安夜）\n");
        }

        if (context.getExiled() != null) {
            sb.append("【被投票放逐】").append(context.getExiled()).append('\n');
        }
        if (context.getWinnerCamp() != null) {
            sb.append("【胜方】")
              .append("WOLF".equals(context.getWinnerCamp()) ? "狼人阵营" : "好人阵营")
              .append('\n');
        }
        if (context.getAliveSeats() != null) {
            sb.append("【现在存活】").append(context.getAliveSeats()).append('\n');
        }
        if (context.getDeadSeats() != null && !context.getDeadSeats().isBlank()) {
            sb.append("【已经出局】").append(context.getDeadSeats()).append('\n');
        }

        sb.append("\n请说一句。");
        return sb.toString();
    }

    private String momentLabel(String moment) {
        return switch (moment == null ? "" : moment) {
            case "DAWN" -> "天亮了，宣布昨夜结果";
            case "VOTE" -> "投票结束，宣布放逐结果";
            case "GAME_END" -> "游戏结束，宣布胜负";
            default -> "宣布一个结果";
        };
    }
}
