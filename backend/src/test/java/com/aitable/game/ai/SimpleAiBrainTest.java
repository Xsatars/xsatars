package com.aitable.game.ai;

import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionResult;
import com.aitable.game.core.GamePhase;
import com.aitable.game.werewolf.WerewolfEngine;
import com.aitable.game.werewolf.WerewolfPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则 AI 的测试。
 *
 * <p>关键的一条：<b>5 个 AI 能自己把一整局打完</b>。
 * 这证明"AI 决策 → 请求引擎 → 引擎校验"这条链路是通的，
 * 而且 AI 每一步给出的动作都能通过引擎的规则校验（没有乱来）。</p>
 */
class SimpleAiBrainTest {

    private final SimpleAiBrain brain = new SimpleAiBrain();

    /** 造一局 6 个玩家、全部标记为 AI 的对局 */
    private WerewolfEngine newAllAiEngine() {
        List<WerewolfPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            WerewolfPlayer player = new WerewolfPlayer(seat, 100L + seat, "AI" + seat);
            player.setAi(true);
            players.add(player);
        }
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players);
        engine.start();
        return engine;
    }

    /** 找出一个"现在有动作可做"的座位，没有则返回 -1 */
    private int findActor(WerewolfEngine engine) {
        for (WerewolfPlayer player : engine.getState().getPlayers()) {
            if (!engine.optionsFor(player.getSeatNo()).isEmpty()) {
                return player.getSeatNo();
            }
        }
        return -1;
    }

    @Test
    @DisplayName("★ 全 AI 对局能自动打完，而且每一步都合法")
    void allAiPlayersCanFinishAGame() {
        WerewolfEngine engine = newAllAiEngine();

        int steps = 0;
        while (!engine.isFinished() && steps < 500) {
            int actor = findActor(engine);
            if (actor < 0) {
                break;   // 理论上不该发生；真发生说明对局卡住了
            }
            ActionRequest request = brain.decide(engine.viewFor(actor));
            assertNotNull(request, actor + " 号位有动作可做，AI 就应该给出决定");

            ActionResult result = engine.apply(request);
            assertTrue(result.isSuccess(),
                    "AI 的动作被引擎拒绝了（" + actor + " 号位，" + request.getType() + "）：" + result.getMessage());
            steps++;
        }

        assertTrue(engine.isFinished(), "AI 应该能自己把一局打完（共走了 " + steps + " 步）");
        assertNotNull(engine.winnerCamp(), "应该分出胜负或平局");
        assertEquals(GamePhase.FINISHED, engine.phase());
    }

    @Test
    @DisplayName("★ 狼人不会刀自己的队友")
    void wolfNeverKillsTeammate() {
        WerewolfEngine engine = newAllAiEngine();
        WerewolfPlayer wolf = engine.getState().aliveWolves().get(0);
        List<Integer> teammates = new ArrayList<>(engine.getState().wolfSeats());

        // 让这只狼反复决策，目标永远不该落在狼身上
        for (int i = 0; i < 50; i++) {
            ActionRequest request = brain.decide(engine.viewFor(wolf.getSeatNo()));
            if (request != null && request.getTargetSeat() != null) {
                assertFalse(teammates.contains(request.getTargetSeat()),
                        "AI 狼人不能刀队友，却选了 " + request.getTargetSeat() + " 号");
            }
        }
    }

    @Test
    @DisplayName("没有可做的动作时，AI 老老实实返回 null（不乱来）")
    void aiReturnsNullWhenNothingToDo() {
        WerewolfEngine engine = newAllAiEngine();
        WerewolfPlayer villager = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() != null && !p.isWolf()
                        && p.getRole() != com.aitable.common.enums.Role.SEER
                        && p.getRole() != com.aitable.common.enums.Role.WITCH)
                .findFirst().orElseThrow();

        // 第一夜：平民既不能刀人也不能查验用药，所以没有可做的动作
        assertTrue(engine.optionsFor(villager.getSeatNo()).isEmpty(), "平民第一夜没有可做的动作");
        assertNull(brain.decide(engine.viewFor(villager.getSeatNo())),
                "没有可做的动作时，AI 应该返回 null，而不是硬凑一个动作");
    }
}
