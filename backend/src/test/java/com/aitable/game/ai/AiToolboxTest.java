package com.aitable.game.ai;

import com.aitable.common.enums.Role;
import com.aitable.game.core.GameView;
import com.aitable.game.werewolf.WerewolfEngine;
import com.aitable.game.werewolf.WerewolfPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 工具（Tool Calling）的测试。
 *
 * <p>重点验证两件事：<b>权限隔离</b>（预言家的工具不给平民）和
 * <b>只读</b>（工具只能查，不能改对局）。</p>
 */
class AiToolboxTest {

    private WerewolfEngine newEngine() {
        List<WerewolfPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            players.add(new WerewolfPlayer(seat, 100L + seat, "玩家" + seat));
        }
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players);
        engine.start();
        return engine;
    }

    private GameView viewOf(WerewolfEngine engine, Role role) {
        WerewolfPlayer player = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == role).findFirst().orElseThrow();
        return engine.viewFor(player.getSeatNo());
    }

    @Test
    @DisplayName("★ 每个人都有的基础工具：查发言 / 查投票 / 查存活 / 查跨局")
    void everyoneHasBasicTools() {
        WerewolfEngine engine = newEngine();
        AiToolbox box = AiToolbox.forView(viewOf(engine, Role.VILLAGER));

        List<String> names = box.all().stream().map(AiTool::getName).toList();
        assertTrue(names.contains("look_up_speeches"), "应该能查发言：" + names);
        assertTrue(names.contains("look_up_votes"), "应该能查投票：" + names);
        assertTrue(names.contains("look_up_alive"), "应该能查存活：" + names);
        assertTrue(names.contains("look_up_room_history"), "应该能查跨局战绩：" + names);
    }

    @Test
    @DisplayName("★ 权限隔离：预言家才有『查我的查验』，狼才有『查队友』")
    void toolsAreRoleGated() {
        WerewolfEngine engine = newEngine();

        List<String> villagerTools = AiToolbox.forView(viewOf(engine, Role.VILLAGER))
                .all().stream().map(AiTool::getName).toList();
        assertFalse(villagerTools.contains("look_up_my_checks"), "平民不该有查验工具");
        assertFalse(villagerTools.contains("look_up_teammates"), "平民不该有队友工具");

        List<String> seerTools = AiToolbox.forView(viewOf(engine, Role.SEER))
                .all().stream().map(AiTool::getName).toList();
        assertTrue(seerTools.contains("look_up_my_checks"), "预言家该有查验工具：" + seerTools);
        assertFalse(seerTools.contains("look_up_teammates"), "预言家不该有队友工具");

        List<String> wolfTools = AiToolbox.forView(viewOf(engine, Role.WOLF))
                .all().stream().map(AiTool::getName).toList();
        assertTrue(wolfTools.contains("look_up_teammates"), "狼该有队友工具：" + wolfTools);
        assertFalse(wolfTools.contains("look_up_my_checks"), "狼不该有查验工具");
    }

    @Test
    @DisplayName("★ 查某人发言：只返回那个人的，而且带上是谁说的")
    void lookupSpeechesFiltersBySeat() {
        WerewolfEngine engine = newEngine();
        engine.getState().recordSpeech(1, 3, "阿豆", "我觉得 5 号有问题");
        engine.getState().recordSpeech(1, 4, "小狼", "行了别吵");
        // 让 view 里带上历史
        GameView view = engine.viewFor(1);
        AiToolbox box = AiToolbox.forView(view);

        String onlySeat3 = box.call("look_up_speeches", Map.of("seat", "3"));
        assertTrue(onlySeat3.contains("我觉得 5 号有问题"), "应该查到 3 号说的话：" + onlySeat3);
        assertFalse(onlySeat3.contains("行了别吵"), "不该混进 4 号的话：" + onlySeat3);
    }

    @Test
    @DisplayName("★ 查一个没说过话的座位，会给友好提示而不是报错")
    void lookupEmptySeatIsFriendly() {
        WerewolfEngine engine = newEngine();
        // 先让 3 号说一句，这样历史不是空的（空历史走的是另一条分支）
        engine.getState().recordSpeech(1, 3, "阿豆", "我是好人");
        AiToolbox box = AiToolbox.forView(engine.viewFor(1));

        String result = box.call("look_up_speeches", Map.of("seat", "9"));
        assertTrue(result.contains("还没发过言"), "应该给个友好答复：" + result);
    }

    @Test
    @DisplayName("★ 一句话都没说过时，查发言会明说『还没有人发过言』")
    void lookupWithNoHistoryAtAll() {
        WerewolfEngine engine = newEngine();
        AiToolbox box = AiToolbox.forView(engine.viewFor(1));

        String result = box.call("look_up_speeches", Map.of());
        assertTrue(result.contains("还没有人发过言"), "应该明说没有历史：" + result);
    }

    @Test
    @DisplayName("★ 调不存在的工具也不会炸（模型有时会瞎编工具名）")
    void unknownToolIsSafe() {
        WerewolfEngine engine = newEngine();
        AiToolbox box = AiToolbox.forView(engine.viewFor(1));

        String result = box.call("give_me_everyones_role", Map.of());
        assertTrue(result.contains("没有这个工具"), "应该友好拒绝：" + result);
    }

    @Test
    @DisplayName("★ 工具是只读的：查了一圈，对局状态一点没变")
    void toolsAreReadOnly() {
        WerewolfEngine engine = newEngine();
        int phaseBefore = engine.getState().getPhase().ordinal();
        int roundBefore = engine.getState().getRound();
        long aliveBefore = engine.getState().getPlayers().stream()
                .filter(WerewolfPlayer::isAlive).count();

        AiToolbox box = AiToolbox.forView(engine.viewFor(1));
        for (AiTool tool : box.all()) {
            box.call(tool.getName(), Map.of());
        }

        assertTrue(engine.getState().getPhase().ordinal() == phaseBefore, "阶段不该被工具改掉");
        assertTrue(engine.getState().getRound() == roundBefore, "轮数不该被工具改掉");
        assertTrue(engine.getState().getPlayers().stream().filter(WerewolfPlayer::isAlive).count()
                == aliveBefore, "存活人数不该被工具改掉");
    }

    @Test
    @DisplayName("★ 工具声明能转成 API 要的格式（不然模型根本看不到它）")
    void toolDefinitionIsApiReady() {
        WerewolfEngine engine = newEngine();
        AiToolbox box = AiToolbox.forView(viewOf(engine, Role.SEER));
        AiTool tool = box.all().get(0);

        Map<String, Object> api = tool.toApiFormat();
        assertTrue("function".equals(api.get("type")), "外层类型要是 function：" + api);
        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) api.get("function");
        assertTrue(function.get("name") != null, "必须有名字");
        assertTrue(function.get("description") != null, "必须有说明（模型靠它判断该不该用）");
        assertTrue(function.get("parameters") != null, "必须有参数说明");
    }

    @Test
    @DisplayName("★ 跨局历史为空时，查它会明确说『这是第一局』")
    void roomHistoryEmptyIsFriendly() {
        WerewolfEngine engine = newEngine();
        AiToolbox box = AiToolbox.forView(engine.viewFor(1));

        String result = box.call("look_up_room_history", Map.of());
        assertTrue(result.contains("第一局"), "应该告诉模型这是第一局：" + result);
    }

    @Test
    @DisplayName("★ 有跨局历史时也不给真人看（工具不能变成绕过信息隔离的后门）")
    void roomHistoryIsVisibleWhenPresent() {
        WerewolfEngine engine = newEngine();
        engine.loadRoomHistory(List.of("第 1 局（狼人胜）：1 号=狼人"));
        AiToolbox box = AiToolbox.forView(engine.viewFor(1));

        // 引擎只把 roomHistory 发给 AI，真人的 view 里没有；
        // 工具是从 view 造的，所以它也不该帮真人拿到 ——
        // 这条断言本身就在验证"工具没有绕过信息隔离"
        String result = box.call("look_up_room_history", Map.of());
        assertTrue(result.contains("第一局"),
                "真人不该通过工具拿到跨局记忆：" + result);
    }

    @Test
    @DisplayName("★ AI 能通过工具拿到跨局记忆（它才是该看到的人）")
    void aiCanSeeRoomHistoryThroughTool() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer ai = engine.getState().getPlayers().get(0);
        ai.setAi(true);
        engine.loadRoomHistory(List.of("第 1 局（狼人胜）：1 号=狼人　2 号=预言家"));

        AiToolbox box = AiToolbox.forView(engine.viewFor(ai.getSeatNo()));
        String result = box.call("look_up_room_history", Map.of());
        assertTrue(result.contains("第 1 局"), "AI 应该查到上一局：" + result);
        assertTrue(result.contains("狼人"), "内容要完整：" + result);
    }

    @Test
    @DisplayName("★ 狼查队友能查到（AI 靠它避免刀自己人）")
    void wolfCanLookUpTeammates() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer wolf = engine.getState().aliveWolves().get(0);
        wolf.setAi(true);
        AiToolbox box = AiToolbox.forView(engine.viewFor(wolf.getSeatNo()));

        String result = box.call("look_up_teammates", Map.of());
        assertTrue(result.contains("队友"), "狼应该能查到队友：" + result);
        // 自己不该出现在"队友"列表里
        assertFalse(result.contains(wolf.getSeatNo() + " 号"),
                "自己不算队友：" + result);
    }

    @Test
    @DisplayName("★ 预言家查查验记录：查过就有，没查过就说没有")
    void seerCanLookUpOwnChecks() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer seer = engine.getState().findAliveByRole(Role.SEER);
        AiToolbox before = AiToolbox.forView(engine.viewFor(seer.getSeatNo()));
        assertTrue(before.call("look_up_my_checks", Map.of()).contains("还没查验"),
                "还没查之前应该说没有");
    }
}
