package com.aitable.game.ai;

import com.aitable.game.core.GameView;
import com.aitable.game.werewolf.WerewolfEngine;
import com.aitable.game.werewolf.WerewolfPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨局记忆测试。
 *
 * <p>单局记忆让 AI 记得"这一轮谁说了什么"，跨局记忆让它记得
 * "上一个房间里那局，3 号是狼，把我骗了"。真人玩家本来就会这么记，
 * AI 有了这份印象，一桌人的"关系"才能跨局延续。</p>
 */
class RoomMemoryTest {

    private WerewolfEngine newEngine() {
        List<WerewolfPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            WerewolfPlayer p = new WerewolfPlayer(seat, 100L + seat, "玩家" + seat);
            players.add(p);
        }
        return WerewolfEngine.create(1L, 1L, players);
    }

    @Test
    @DisplayName("★ 装载的跨局记忆会出现在 AI 的 view 里")
    void roomHistoryReachesAiView() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer ai = engine.getState().getPlayers().get(0);
        ai.setAi(true);
        ai.setPersona(AiPersona.ANALYST);

        engine.loadRoomHistory(List.of(
                "第 1 局（狼人胜）：1 号=狼人　2 号=预言家　3 号=平民",
                "第 2 局（好人胜）：1 号=平民　2 号=狼人　3 号=女巫"));
        engine.start();

        GameView view = engine.viewFor(ai.getSeatNo());
        Object history = view.getPrivateState().getInfo().get("roomHistory");
        assertNotNull(history, "AI 应该拿得到跨局记忆");
        assertTrue(history instanceof List<?> list && list.size() == 2,
                "应该有 2 条历史，实际：" + history);
        assertTrue(history.toString().contains("第 1 局"), "历史内容要带上局号");
    }

    @Test
    @DisplayName("★ 真人看不到跨局记忆（那是给 AI 用的）")
    void humanDoesNotSeeRoomHistory() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer human = engine.getState().getPlayers().get(0);
        // 注意：human 没有 setAi(true)

        engine.loadRoomHistory(List.of("第 1 局（狼人胜）：1 号=狼人"));
        engine.start();

        assertFalse(engine.viewFor(human.getSeatNo()).getPrivateState().getInfo()
                .containsKey("roomHistory"), "真人的 view 里不该有跨局记忆");
    }

    @Test
    @DisplayName("★ 没有历史时不塞空内容（新房间第一次开局）")
    void noHistoryMeansNoKey() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer ai = engine.getState().getPlayers().get(0);
        ai.setAi(true);

        engine.loadRoomHistory(List.of());
        engine.start();

        assertFalse(engine.viewFor(ai.getSeatNo()).getPrivateState().getInfo()
                .containsKey("roomHistory"), "空历史不该出现在 view 里");
    }

    @Test
    @DisplayName("★ loadRoomHistory(null) 不会炸（第一次开局常常传 null）")
    void nullHistoryIsSafe() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer ai = engine.getState().getPlayers().get(0);
        ai.setAi(true);

        engine.loadRoomHistory(null);
        engine.start();

        assertTrue(engine.getState().getRoomHistory().isEmpty(), "null 应该当作空处理");
        assertFalse(engine.viewFor(ai.getSeatNo()).getPrivateState().getInfo()
                .containsKey("roomHistory"));
    }

    @Test
    @DisplayName("★ 重复装载会覆盖，不是叠加（第二局要看的是最新历史）")
    void loadingTwiceReplaces() {
        WerewolfEngine engine = newEngine();
        engine.loadRoomHistory(List.of("旧的一局"));
        engine.loadRoomHistory(List.of("新的第一局", "新的第二局"));

        assertEquals(2, engine.getState().getRoomHistory().size(), "应该是覆盖而不是累加");
        assertTrue(engine.getState().getRoomHistory().contains("新的第一局"));
        assertFalse(engine.getState().getRoomHistory().contains("旧的一局"));
    }
}
