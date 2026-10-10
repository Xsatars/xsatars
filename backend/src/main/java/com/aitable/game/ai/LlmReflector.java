package com.aitable.game.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 用大模型做复盘。
 *
 * <p>这时候身份已经公开了，所以可以把"我是谁、对面是谁、我哪步判断错了"都摊开说 ——
 * 复盘的价值就在于知道真相之后回头看。</p>
 *
 * <p>调不通就返回 {@code null}，上层不会写这条复盘（下一局少一句"教训"，不影响玩）。</p>
 */
public class LlmReflector implements AiReflector {

    private static final Logger log = LoggerFactory.getLogger(LlmReflector.class);

    private final DeepSeekClient client;

    public LlmReflector(DeepSeekClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "大模型复盘";
    }

    @Override
    public String reflect(ReflectContext context) {
        String reply = client.chatText(systemPrompt(), userPrompt(context));
        if (reply == null || reply.isBlank()) {
            return null;
        }
        return clean(reply);
    }

    private String clean(String reply) {
        String text = reply.trim();
        if (text.length() > 1 && (text.startsWith("\"") && text.endsWith("\""))) {
            text = text.substring(1, text.length() - 1).trim();
        }
        int nl = text.indexOf('\n');
        if (nl > 0) {
            text = text.substring(0, nl).trim();
        }
        if (text.length() > 200) {
            text = text.substring(0, 200);
        }
        if (text.isBlank()) {
            log.debug("复盘没生成内容，跳过");
            return null;
        }
        return text;
    }

    private String systemPrompt() {
        return """
                你刚打完一局 6 人狼人杀。现在**身份已经全部公开**，回头看你这一局。

                请用第一人称说一句复盘，要求：
                - 20~50 字，就一句
                - **说具体的**：哪一步判断错了、哪一句话说漏了、或者哪次赌对了
                - 用你自己的口吻（下面会给你人设），别写成总结报告
                - 不要用 emoji，不要加"复盘："这种前缀

                反例（太笼统，别这样写）：「我下次要更小心。」
                正例：「我第 2 轮不该跟票 —— 3 号那句话明显在带节奏，我当时太急了。」
                """;
    }

    private String userPrompt(ReflectContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("【你的人设】").append(context.getPersonaStyle() == null
                ? "普通玩家" : context.getPersonaStyle()).append('\n');
        sb.append("【你是几号】").append(context.getSeatNo()).append(" 号 ")
                .append(context.getNickname() == null ? "" : context.getNickname()).append('\n');
        sb.append("【你这局的身份】").append(context.getRoleLabel())
                .append("（").append("WOLF".equals(context.getCamp()) ? "狼人阵营" : "好人阵营").append("）\n");
        sb.append("【结果】").append(context.isWin() ? "赢了" : "输了")
                .append(context.isSurvived() ? "，活到了最后" : "，中途出局").append('\n');
        if (context.getDeathReason() != null) {
            sb.append("【怎么出局的】").append(context.getDeathReason()).append('\n');
        }
        sb.append("【打了几轮】").append(context.getRounds()).append(" 轮\n");

        List<String> speeches = context.getSpeeches();
        if (speeches != null && !speeches.isEmpty()) {
            sb.append("\n【你这局说过的话】\n");
            int from = Math.max(0, speeches.size() - 6);
            for (int i = from; i < speeches.size(); i++) {
                sb.append("- ").append(speeches.get(i)).append('\n');
            }
        }
        List<String> votes = context.getVotes();
        if (votes != null && !votes.isEmpty()) {
            sb.append("\n【这局的投票】\n");
            for (String line : votes) {
                sb.append("- ").append(line).append('\n');
            }
        }

        sb.append("\n请说一句你的复盘。");
        return sb.toString();
    }
}
