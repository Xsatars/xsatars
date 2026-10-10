package com.aitable.game.ai;

import com.aitable.game.core.GameView;
import com.aitable.game.core.PlayerPrivateState;
import com.aitable.game.core.PublicGameState;
import com.aitable.game.core.SeatView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个 AI 手边能用的工具集合。
 *
 * <p>两个关键点：</p>
 * <ol>
 *   <li><b>按权限注册</b>：预言家才有"查我的查验"这个工具，狼才有"查队友" ——
 *       平民连工具名都看不到。就算模型胡乱调用，也拿不到它不该知道的。</li>
 *   <li><b>只从 {@link GameView} 构造</b>：工具能看到的，正好是这个 AI 能看到的东西，
 *       不多一点。这样"工具"不会变成绕过信息隔离的后门。</li>
 * </ol>
 *
 * <p>所有工具都是<b>只读</b>：它们只能查，改状态仍然必须走
 * {@code ActionRequest} → 引擎校验那条路。</p>
 */
public class AiToolbox {

    private final Map<String, AiTool> tools = new LinkedHashMap<>();

    private AiToolbox() {
    }

    /** 给这个 AI 造一套工具（能看到的 = view 里有的） */
    public static AiToolbox forView(GameView view) {
        AiToolbox box = new AiToolbox();
        if (view == null || view.getPublicState() == null || view.getPrivateState() == null) {
            return box;
        }
        PublicGameState pub = view.getPublicState();
        PlayerPrivateState priv = view.getPrivateState();

        box.registerSpeeches(pub);
        box.registerVotes(pub);
        box.registerAlive(pub);
        box.registerRoomHistory(priv);

        if ("SEER".equals(priv.getMyRole())) {
            box.registerMyChecks(priv);
        }
        if ("WOLF".equals(priv.getMyRole())) {
            box.registerTeammates(priv);
        }
        return box;
    }

    public List<AiTool> all() {
        return new ArrayList<>(tools.values());
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }

    /** 调用一个工具；不认识的名字也要给个正常答复，别让模型懵在那儿 */
    public String call(String name, Map<String, Object> arguments) {
        AiTool tool = tools.get(name);
        if (tool == null) {
            return "（没有这个工具：" + name + "）";
        }
        return tool.execute(arguments);
    }

    private void register(AiTool tool) {
        tools.put(tool.getName(), tool);
    }

    // ============================================================
    // 具体工具
    // ============================================================

    private void registerSpeeches(PublicGameState pub) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("seat", "只看某个座位说过的话（填座位号，比如 3）；不填就返回最近所有人的发言");
        register(new AiTool("look_up_speeches",
                "查之前几轮的发言记录。想翻某人说过什么、有没有前后矛盾、谁在带节奏，就查这个。",
                params,
                args -> {
                    List<String> lines = pub.getSpeechHistory() == null
                            ? List.of() : pub.getSpeechHistory();
                    if (lines.isEmpty()) {
                        return "还没有人发过言";
                    }
                    Object seatArg = args.get("seat");
                    if (seatArg == null || String.valueOf(seatArg).isBlank()) {
                        int from = Math.max(0, lines.size() - 24);
                        return String.join("\n", lines.subList(from, lines.size()));
                    }
                    String seat = String.valueOf(seatArg).replaceAll("[^0-9]", "");
                    if (seat.isEmpty()) {
                        return String.join("\n", lines);
                    }
                    List<String> filtered = new ArrayList<>();
                    for (String line : lines) {
                        if (line.contains("　" + seat + " 号 ") || line.contains(seat + " 号 ")) {
                            filtered.add(line);
                        }
                    }
                    return filtered.isEmpty()
                            ? seat + " 号还没发过言"
                            : String.join("\n", filtered);
                }));
    }

    private void registerVotes(PublicGameState pub) {
        register(new AiTool("look_up_votes",
                "查之前每一轮的投票明细（谁投了谁、谁被放逐、有没有平票）。"
                        + "想分析谁跟谁抱团、谁临阵改票，就查这个。",
                Map.of(),
                args -> {
                    List<String> lines = pub.getVoteHistory() == null
                            ? List.of() : pub.getVoteHistory();
                    return lines.isEmpty() ? "还没投过票" : String.join("\n", lines);
                }));
    }

    private void registerAlive(PublicGameState pub) {
        register(new AiTool("look_up_alive",
                "查现在还有谁活着、谁已经出局（带名字）。",
                Map.of(),
                args -> {
                    StringBuilder alive = new StringBuilder();
                    StringBuilder dead = new StringBuilder();
                    for (SeatView seat : pub.getSeats()) {
                        StringBuilder target = seat.isAlive() ? alive : dead;
                        if (target.length() > 0) {
                            target.append('、');
                        }
                        target.append(seat.getSeatNo()).append(" 号 ").append(seat.getNickname());
                    }
                    return "存活：" + alive + "\n已出局：" + (dead.length() == 0 ? "无" : dead);
                }));
    }

    private void registerRoomHistory(PlayerPrivateState priv) {
        register(new AiTool("look_up_room_history",
                "查这个房间之前几局的结果（谁在几号位是什么身份、哪边赢了、各 AI 上局怎么复盘的）。"
                        + "想用「你上局就是狼」这种话挤对人，就查这个。",
                Map.of(),
                args -> {
                    Object raw = priv.getInfo() == null ? null : priv.getInfo().get("roomHistory");
                    if (!(raw instanceof List<?> history) || history.isEmpty()) {
                        return "这是本房间的第一局，还没有历史";
                    }
                    StringBuilder sb = new StringBuilder();
                    history.forEach(line -> sb.append(line).append('\n'));
                    return sb.toString().trim();
                }));
    }

    private void registerMyChecks(PlayerPrivateState priv) {
        register(new AiTool("look_up_my_checks",
                "查你自己（预言家）之前查验过谁、结果是什么。别重复查同一个人，翻一下就行。",
                Map.of(),
                args -> {
                    Object raw = priv.getInfo() == null ? null : priv.getInfo().get("seerResults");
                    if (!(raw instanceof Map<?, ?> results) || results.isEmpty()) {
                        return "你还没查验过任何人";
                    }
                    StringBuilder sb = new StringBuilder();
                    results.forEach((seat, isWolf) -> sb.append("　").append(seat).append(" 号：")
                            .append(Boolean.TRUE.equals(isWolf) ? "狼人" : "好人").append('\n'));
                    return sb.toString().trim();
                }));
    }

    private void registerTeammates(PlayerPrivateState priv) {
        register(new AiTool("look_up_teammates",
                "查你的狼队友都是谁（只有狼能调）。确认别刀自己人、商量集火谁的时候用。",
                Map.of(),
                args -> {
                    List<Integer> mates = priv.getTeammateSeats();
                    if (mates == null || mates.isEmpty()) {
                        return "你这边没有队友了";
                    }
                    StringBuilder sb = new StringBuilder("狼队友：");
                    for (Integer seat : mates) {
                        sb.append(seat).append(" 号　");
                    }
                    return sb.toString().trim();
                }));
    }
}
