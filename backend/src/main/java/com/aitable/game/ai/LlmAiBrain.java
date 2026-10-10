package com.aitable.game.ai;

import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionType;
import com.aitable.game.core.GameView;
import com.aitable.game.core.PlayerPrivateState;
import com.aitable.game.core.PublicGameState;
import com.aitable.game.core.SeatView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用大模型做决策的脑子。
 *
 * <p>它做的事情很单纯：把"这个 AI 现在能看到的东西"写成人话发给模型，
 * 让模型回一个 JSON 动作，然后翻译成 {@link ActionRequest} 交给引擎。</p>
 *
 * <p><b>两道保险</b>，保证 AI 出错也不会把对局搞卡：</p>
 * <ol>
 *   <li>模型返回的动作必须在"你现在能做的动作"白名单里，否则不采纳</li>
 *   <li>只要调用失败、超时、返回看不懂的东西，立刻<b>回退到规则 AI</b>——
 *       宁可它打得笨一点，也不能让一桌人干等</li>
 * </ol>
 */
public class LlmAiBrain implements AiBrain {

    private static final Logger log = LoggerFactory.getLogger(LlmAiBrain.class);

    private final DeepSeekClient client;
    private final AiBrain fallback;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 每个 AI 的性格设定，让它们的发言不那么像一个模子刻出来的 */
    private final String personality;

    public LlmAiBrain(DeepSeekClient client, AiBrain fallback) {
        this(client, fallback, "说话直接、不喜欢绕弯子");
    }

    public LlmAiBrain(DeepSeekClient client, AiBrain fallback, String personality) {
        this.client = client;
        this.fallback = fallback;
        this.personality = personality;
    }

    @Override
    public String name() {
        return "大模型AI";
    }

    @Override
    public ActionRequest decide(GameView view) {
        if (view == null || view.getPublicState() == null || view.getPrivateState() == null) {
            return null;
        }
        List<String> actions = view.getPrivateState().getAvailableActions();
        if (actions == null || actions.isEmpty()) {
            return null;   // 这一步本来就没它的事
        }

        int seat = view.getPrivateState().getMySeat();

        // ---------- 第一步：把工具给它，看它想不想先查点什么 ----------
        StringBuilder gathered = new StringBuilder();
        List<String> usedTools = new ArrayList<>();
        AiToolbox toolbox = AiToolbox.forView(view);
        if (!toolbox.isEmpty()) {
            String firstReply = client.chatRaw(systemPrompt(view), userPrompt(view), toolbox.all());
            List<ToolCall> calls = parseToolCalls(firstReply);
            if (calls.isEmpty() && firstReply != null) {
                // 没调工具是正常的（"我觉得不用查"），但调不通就是另一回事了，记一下便于排查
                log.debug("AI {} 号位这次没查工具，原始响应前 200 字：{}", seat, brief(firstReply));
            }
            for (ToolCall call : calls) {
                String result = toolbox.call(call.name, call.arguments);
                usedTools.add(call.name);
                log.info("🔧 AI {} 号位查了「{}」：{}", seat, call.name, brief(result));
                gathered.append("【你查「").append(call.name).append("」得到的结果】\n")
                        .append(result).append("\n\n");
            }
        }

        // ---------- 第二步：带着查到的材料，让它给出动作 ----------
        // 这一轮**不给工具** —— 强制它落地，避免它一直查下去不做事
        String prompt = userPrompt(view);
        if (gathered.length() > 0) {
            prompt = prompt + "\n\n" + gathered + "现在请根据上面查到的结果，给出你的动作。";
        }

        String reply = client.chat(systemPrompt(view), prompt);
        ActionRequest request = parse(reply, view, actions);
        if (request != null) {
            // 把 AI 说的话记下来，方便人工看看它是不是在说人话
            if (request.getType() == ActionType.SPEAK) {
                log.info("AI {} 号位发言：{}", seat, request.getText());
            } else {
                log.debug("AI {} 号位决定：{}{}", seat, request.getType(),
                        request.getTargetSeat() == null ? "" : " → " + request.getTargetSeat() + " 号");
            }
            return request;
        }

        // 没拿到能用的动作 → 规则 AI 顶上，绝不空转
        log.debug("{} 号位的大模型决策不可用，回退规则 AI", seat);
        return fallback.decide(view);
    }

    /** 一次工具调用的意图 */
    private static class ToolCall {
        private final String name;
        private final Map<String, Object> arguments;

        ToolCall(String name, Map<String, Object> arguments) {
            this.name = name;
            this.arguments = arguments;
        }
    }

    /**
     * 从模型的原始响应里解析出"它想调哪些工具"。
     *
     * <p>OpenAI 兼容格式下长这样：</p>
     * <pre>
     * {"choices":[{"message":{"tool_calls":[
     *   {"id":"call_1","function":{"name":"look_up_votes","arguments":"{\"seat\":\"3\"}"}}
     * ]}}]}
     * </pre>
     * <p>注意 {@code arguments} 是一段<b>字符串形式的 JSON</b>，要再解析一次。</p>
     */
    private List<ToolCall> parseToolCalls(String rawResponse) {
        List<ToolCall> result = new ArrayList<>();
        if (rawResponse == null || rawResponse.isBlank()) {
            return result;
        }
        try {
            JsonNode toolCalls = mapper.readTree(rawResponse)
                    .path("choices").path(0).path("message").path("tool_calls");
            if (!toolCalls.isArray()) {
                return result;   // 模型决定直接回答，没调工具，也正常
            }
            for (JsonNode call : toolCalls) {
                JsonNode function = call.path("function");
                String name = function.path("name").asText("");
                if (name.isBlank()) {
                    continue;
                }
                Map<String, Object> args = new LinkedHashMap<>();
                String rawArgs = function.path("arguments").asText("");
                if (!rawArgs.isBlank()) {
                    try {
                        JsonNode parsed = mapper.readTree(rawArgs);
                        parsed.fields().forEachRemaining(entry ->
                                args.put(entry.getKey(), entry.getValue().asText()));
                    } catch (Exception ignored) {
                        // 参数解析不了就当没传参数，工具自己有默认行为
                    }
                }
                result.add(new ToolCall(name, args));
            }
        } catch (Exception e) {
            log.debug("解析工具调用失败：{}", e.getMessage());
        }
        // 一轮最多认 3 个工具，防止模型一次调一堆把提示词撑爆
        return result.size() > 3 ? result.subList(0, 3) : result;
    }

    /** 日志里只留一小段，别把整个查询结果刷屏 */
    private String brief(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replace('\n', ' ').trim();
        return flat.length() > 120 ? flat.substring(0, 120) + "…" : flat;
    }

    // ============================================================
    // 提示词
    // ============================================================

    /**
     * 系统提示。
     *
     * <p>人设是<b>从这一局的这个 AI 自己身上读的</b>（引擎把他的 AiPersona 放进了 info），
     * 不是所有 AI 共用一套 —— 这才有"一桌人脾气各不相同"的效果。
     * 万一拿不到（比如老数据没存性格），退回构造时给的默认语气。</p>
     */
    private String systemPrompt(GameView view) {
        String style = personality;
        Map<String, Object> info = view.getPrivateState().getInfo();
        if (info != null && info.get("personaStyle") instanceof String fromEngine
                && !fromEngine.isBlank()) {
            style = fromEngine;
        }

        return """
                你在玩一局 6 人狼人杀，是其中一名玩家。
                规则：狼人夜里刀人；预言家每夜查验一人身份；女巫有一瓶解药一瓶毒药；
                白天所有人依次发言，然后投票放逐一人。狼人杀光好人、或好人放逐全部狼人即结束。

                你的人设：%s

                你必须【只输出一个 JSON 对象】，不要有任何解释、不要加代码块标记：
                  选目标：{"action":"动作名","target":座位号}
                  发言：  {"action":"SPEAK","text":"你要说的话"}
                  放弃：  {"action":"PASS"}

                硬性要求：
                - action 必须是"你现在可做的动作"里列出的那个
                - target 必须是"还能被选的人"里的座位号
                - 发言要像真人一样自然、口语化，20~40 字，不要暴露自己是程序
                - 语气、用词要符合你的人设（急性子就催、阴阳怪气就带刺、冷静分析就讲证据）
                - 【重要】如果发言记录或投票记录里有值得拎出来说的（谁改过口、谁跟你投了同一个人、
                  谁明显在说谎），主动点它一句，别每轮都只顾着说自己

                你还可以**先查资料再决定**：手边有几个查询工具（翻某人说过的话、看历史票型、
                查存活名单、查这个房间上几局的战绩和别人的复盘）。
                想查就调工具；已经想清楚了就直接给动作，不用为了查而查。
                """.formatted(style);
    }

    private String userPrompt(GameView view) {
        PublicGameState pub = view.getPublicState();
        PlayerPrivateState priv = view.getPrivateState();

        StringBuilder sb = new StringBuilder();
        sb.append("【当前局面】\n");
        sb.append("第 ").append(pub.getRound()).append(" 轮 · ")
                .append(pub.getPhaseLabel() == null ? pub.getPhase() : pub.getPhaseLabel()).append('\n');
        sb.append("存活：").append(aliveSeats(pub)).append('\n');
        if (pub.getAliveCount() > 0) {
            sb.append("场上还有 ").append(pub.getAliveCount()).append(" 人\n");
        }

        sb.append("\n【你的身份】\n");
        sb.append("你是 ").append(priv.getMySeat()).append(" 号位，")
                .append(priv.getMyRoleLabel() == null ? priv.getMyRole() : priv.getMyRoleLabel());
        if (priv.getMyCamp() != null) {
            sb.append("（").append("WOLF".equals(priv.getMyCamp()) ? "狼人阵营" : "好人阵营").append("）");
        }
        sb.append('\n');

        // 只有这个角色自己知道的信息（查验结果、队友等）
        String secrets = secrets(priv);
        if (!secrets.isEmpty()) {
            sb.append("\n【只有你知道的信息】\n").append(secrets);
        }

        sb.append("\n【你现在可做的动作】\n");
        sb.append(String.join("、", priv.getAvailableActions())).append('\n');
        // 动作名是英文枚举，个别的不解释一下模型会懵 —— 尤其 HUNTER_SHOOT 是新加的
        if (priv.getAvailableActions().contains("HUNTER_SHOOT")) {
            sb.append("（HUNTER_SHOOT = 你是猎人，刚被打死，现在可以开枪带走一个人。"
                    + "先用 look_up_alive 看看谁还活着，挑最像狼的那个；"
                    + "实在没把握就给 PASS，不开枪也不算亏。\n"
                    + "这可能是你最后一次说话了 —— 可以带点情绪。）\n");
        }

        List<Integer> selectable = selectableSeats(pub, priv);
        if (!selectable.isEmpty()) {
            sb.append("\n【还能被选的人】\n");
            sb.append(joinSeats(selectable)).append('\n');
        }

        // 记忆③：这个房间之前几局的战绩（跨局）。有了它，AI 才能说出
        // 「上一局 3 号就是狼，这次别信他」——真人玩家本来就记得这些
        Object roomHistory = view.getPrivateState().getInfo() == null
                ? null : view.getPrivateState().getInfo().get("roomHistory");
        if (roomHistory instanceof List<?> history && !history.isEmpty()) {
            sb.append("\n【这个房间之前几局的结果】（跨局记忆，你可以拿去挤对人）\n");
            for (Object line : history) {
                sb.append("- ").append(line).append('\n');
            }
        }

        if (pub.getRecentMessages() != null && !pub.getRecentMessages().isEmpty()) {
            sb.append("\n【刚刚发生了什么】\n");
            for (String msg : pub.getRecentMessages()) {
                sb.append("- ").append(msg).append('\n');
            }
        }

        // 记忆①：前面几轮谁说过什么。AI 可以引用它 ——
        // 「3 号你昨天还说要投我，今天怎么改口了」这种话就靠这段
        List<String> speeches = pub.getSpeechHistory();
        if (speeches != null && !speeches.isEmpty()) {
            sb.append("\n【之前几轮的发言记录】（按时间顺序，最新在后面；你可以引用）\n");
            int from = Math.max(0, speeches.size() - 18);
            for (int i = from; i < speeches.size(); i++) {
                sb.append("- ").append(speeches.get(i)).append('\n');
            }
        }

        // 记忆②：前面几轮的投票。谁跟谁抱团、谁在关键时候改票，都藏在这里
        List<String> votes = pub.getVoteHistory();
        if (votes != null && !votes.isEmpty()) {
            sb.append("\n【之前的投票记录】\n");
            for (String line : votes) {
                sb.append("- ").append(line).append('\n');
            }
        }

        sb.append("\n请决定你的下一步，只回一个 JSON。");
        return sb.toString();
    }

    /** 把私人信息翻译成人话（预言家的查验、狼人的队友） */
    @SuppressWarnings("unchecked")
    private String secrets(PlayerPrivateState priv) {
        StringBuilder sb = new StringBuilder();
        if (priv.getTeammateSeats() != null && !priv.getTeammateSeats().isEmpty()) {
            sb.append("- 你的狼队友是：").append(joinSeats(priv.getTeammateSeats()))
                    .append("（别刀他们，也别在发言里卖他们）\n");
        }

        Map<String, Object> info = priv.getInfo();
        if (info != null && info.get("seerResults") instanceof Map) {
            Map<Object, Object> results = (Map<Object, Object>) info.get("seerResults");
            for (Map.Entry<Object, Object> e : results.entrySet()) {
                sb.append("- 你查验过 ").append(e.getKey()).append(" 号：")
                        .append(Boolean.TRUE.equals(e.getValue()) ? "是狼人" : "是好人").append('\n');
            }
        }
        return sb.toString();
    }

    private String aliveSeats(PublicGameState pub) {
        List<Integer> alive = new ArrayList<>();
        for (SeatView seat : pub.getSeats()) {
            if (seat.isAlive()) {
                alive.add(seat.getSeatNo());
            }
        }
        return joinSeats(alive);
    }

    private List<Integer> selectableSeats(PublicGameState pub, PlayerPrivateState priv) {
        List<Integer> result = new ArrayList<>();
        for (SeatView seat : pub.getSeats()) {
            if (seat.getSeatNo() == priv.getMySeat() || !seat.isAlive()) {
                continue;
            }
            // 狼人不能刀队友，这一点也提前告诉模型，省得它选错被引擎拒
            if (priv.getTeammateSeats() != null && priv.getTeammateSeats().contains(seat.getSeatNo())) {
                continue;
            }
            result.add(seat.getSeatNo());
        }
        return result;
    }

    private String joinSeats(List<Integer> seats) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < seats.size(); i++) {
            if (i > 0) {
                sb.append('、');
            }
            sb.append(seats.get(i)).append(" 号");
        }
        return sb.toString();
    }

    // ============================================================
    // 解析模型返回
    // ============================================================

    private ActionRequest parse(String reply, GameView view, List<String> allowed) {
        if (reply == null || reply.isBlank()) {
            return null;
        }
        String text = reply.trim();
        // 有些模型习惯包一层 ```json ... ```，直接兜一下
        if (text.startsWith("```")) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }
        }

        JsonNode node;
        try {
            node = mapper.readTree(text);
        } catch (Exception e) {
            log.debug("模型返回的不是 JSON：{}", text);
            return null;
        }

        String action = node.path("action").asText("");
        if (action.isBlank() || !allowed.contains(action)) {
            log.debug("模型想做的动作不在允许列表里：{}", action);
            return null;
        }

        int mySeat = view.getPrivateState().getMySeat();

        if (ActionType.SPEAK.name().equals(action)) {
            String speech = node.path("text").asText("").trim();
            if (speech.isBlank()) {
                return null;
            }
            // 别让模型写长篇大论刷屏
            if (speech.length() > 120) {
                speech = speech.substring(0, 120);
            }
            return ActionRequest.speak(mySeat, speech);
        }

        Integer target = null;
        if (node.hasNonNull("target")) {
            target = node.get("target").asInt();
        }
        try {
            return ActionRequest.of(mySeat, ActionType.valueOf(action), target);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
