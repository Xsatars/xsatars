package com.aitable.game.werewolf;

import com.aitable.common.enums.Camp;
import com.aitable.common.enums.Role;
import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionType;
import com.aitable.game.core.GamePhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 胜负判定：<b>屠边</b>。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>狼人全死 → 好人胜</li>
 *   <li>所有平民死光 → 狼人胜（屠民）</li>
 *   <li>所有神职死光 → 狼人胜（屠神）</li>
 * </ul>
 *
 * <p>为什么专门测这个：8 人局里"屠边"和旧的"狼数 ≥ 好人数"<b>结论完全不同</b> ——
 * 杀光 3 个神之后还剩 2 狼 3 民，旧规则会认为游戏没结束，新规则该判狼赢。</p>
 */
class WinConditionTest {

    private WerewolfEngine newEngine(Board board) {
        List<WerewolfPlayer> players = new ArrayList<>();
        int count = board.getPlayerCount();
        for (int seat = 1; seat <= count; seat++) {
            players.add(new WerewolfPlayer(seat, 100L + seat, "玩家" + seat));
        }
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players, 0, board);
        engine.start();
        return engine;
    }

    private List<WerewolfPlayer> byRole(WerewolfEngine engine, Role role) {
        return engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == role).toList();
    }

    /** 触发一次胜负判定：让当前该动的人动一下就行（走一轮夜晚最省事） */
    private void triggerCheck(WerewolfEngine engine) {
        startNightAndSettle(engine);
    }

    @Test
    @DisplayName("★ 8 人局：杀光所有神职 → 狼人胜（屠神）")
    void killingAllGodsWinsForWolves() {
        WerewolfEngine engine = newEngine(Board.WEREWOLF_8);
        WerewolfState state = engine.getState();

        // 先把所有神职弄死
        for (Role god : List.of(Role.SEER, Role.WITCH, Role.HUNTER)) {
            byRole(engine, god).forEach(p -> p.die(WerewolfEngine.DEATH_VOTE));
        }

        // 这时还剩 2 狼 + 3 民 —— 按旧规则（狼数 ≥ 好人数）根本不成立，
        // 但按屠边，神职已经全没了，狼队该赢
        long aliveVillagers = state.alivePlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).count();
        assertEquals(3, aliveVillagers, "平民应该还剩 3 个（我们只杀了神）");
        assertTrue(state.countAliveByCamp(Camp.WOLF) < state.countAliveByCamp(Camp.GOOD),
                "狼数少于好人数 —— 旧规则这时候不会判狼赢，全靠屠边规则");

        assertTrue(engine.evaluateWinConditionNow(), "屠神之后判定应该结束对局");
        assertEquals(Camp.WOLF.name(), state.getWinnerCamp(), "狼人应该赢");
    }

    @Test
    @DisplayName("★ 8 人局：杀光所有平民 → 狼人胜（屠民）")
    void killingAllVillagersWinsForWolves() {
        WerewolfEngine engine = newEngine(Board.WEREWOLF_8);
        WerewolfState state = engine.getState();

        byRole(engine, Role.VILLAGER).forEach(p -> p.die(WerewolfEngine.DEATH_VOTE));

        long aliveGods = state.alivePlayers().stream()
                .filter(p -> p.getRole() != Role.VILLAGER && p.getRole() != Role.WOLF).count();
        assertEquals(3, aliveGods, "神职应该还剩 3 个（我们只杀了民）");

        assertTrue(engine.evaluateWinConditionNow(), "屠民之后判定应该结束对局");
        assertEquals(Camp.WOLF.name(), state.getWinnerCamp(), "狼人应该赢");
    }

    @Test
    @DisplayName("★ 真实流程里也会自动判：狼刀掉最后一个神 → 对局直接结束")
    void realFlowTriggersWinCheck() {
        WerewolfEngine engine = newEngine(Board.WEREWOLF_8);
        WerewolfState state = engine.getState();

        // 只留猎人当"最后一个神"，另外两个神先出局。
        // 特意留猎人：他夜里不行动，不会干扰这一夜的推进。
        byRole(engine, Role.SEER).forEach(p -> p.die(WerewolfEngine.DEATH_VOTE));
        byRole(engine, Role.WITCH).forEach(p -> p.die(WerewolfEngine.DEATH_VOTE));
        WerewolfPlayer hunter = byRole(engine, Role.HUNTER).get(0);

        // 狼去刀猎人 —— 这一刀之后就"屠神"了
        for (WerewolfPlayer wolf : state.aliveWolves()) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL,
                    hunter.getSeatNo()));
        }

        // 猎人被刀死会先进"开枪"环节（这是规则），让他放弃开枪，流程才会继续走到判定
        if (state.getPhase() == GamePhase.HUNTER_SHOOT) {
            engine.apply(ActionRequest.of(hunter.getSeatNo(), ActionType.PASS, null));
        }

        assertEquals(GamePhase.FINISHED, state.getPhase(),
                "所有神职都没了，真实流程应该自动结束对局");
        assertEquals(Camp.WOLF.name(), state.getWinnerCamp(), "狼人应该赢（屠神）");
    }

    @Test
    @DisplayName("★ 还没屠边时不能提前结束（平民和神职都还有活口）")
    void gameContinuesBeforeEitherSideIsWiped() {
        WerewolfEngine engine = newEngine(Board.WEREWOLF_8);
        WerewolfState state = engine.getState();

        // 杀掉 2 个平民、1 个神 —— 两边都还有活口
        List<WerewolfPlayer> villagers = byRole(engine, Role.VILLAGER);
        villagers.get(0).die(WerewolfEngine.DEATH_VOTE);
        villagers.get(1).die(WerewolfEngine.DEATH_VOTE);
        byRole(engine, Role.SEER).forEach(p -> p.die(WerewolfEngine.DEATH_VOTE));

        assertTrue(state.alivePlayers().stream().anyMatch(p -> p.getRole() == Role.VILLAGER),
                "还有平民活着");
        assertTrue(state.alivePlayers().stream()
                        .anyMatch(p -> p.getRole() != Role.VILLAGER && p.getRole() != Role.WOLF),
                "还有神职活着");

        assertFalse(engine.evaluateWinConditionNow(),
                "两边都还有活口，不该结束，实际：" + state.getWinnerCamp());
    }

    @Test
    @DisplayName("★ 6 人局也是屠边（2 平民都死 → 狼赢）")
    void sixPlayerBoardAlsoUsesKillingSides() {
        WerewolfEngine engine = newEngine(Board.WEREWOLF_6);
        WerewolfState state = engine.getState();

        byRole(engine, Role.VILLAGER).forEach(p -> p.die(WerewolfEngine.DEATH_VOTE));
        assertEquals(2, byRole(engine, Role.VILLAGER).size(), "6 人局有 2 个平民");

        assertTrue(engine.evaluateWinConditionNow(), "平民都死光了，判定该结束");
        assertEquals(Camp.WOLF.name(), state.getWinnerCamp(), "狼赢");
    }

    @Test
    @DisplayName("★ 狼人全死 → 好人胜（这条不变）")
    void killingAllWolvesWinsForGood() {
        WerewolfEngine engine = newEngine(Board.WEREWOLF_8);
        WerewolfState state = engine.getState();

        state.aliveWolves().forEach(w -> w.die(WerewolfEngine.DEATH_VOTE));

        // 狼全死之后夜晚没法继续（没人可行动），所以直接判一次 —— 相当于
        // "最后一只狼被投票放逐"的那一刻会做的事
        assertTrue(engine.evaluateWinConditionNow(), "狼全死了，判定应该直接结束对局");
        assertEquals(Camp.GOOD.name(), state.getWinnerCamp(), "好人应该赢");
        // 这里不断言 phase：checkWinCondition 只负责"定下谁赢"，
        // 把状态切成 FINISHED 是 endGame() 的事（那是流程的一部分，见别的测试）
    }

    @Test
    @DisplayName("★ 最后一个狼和最后一批好人同归于尽 → 判好人胜（先判狼全死）")
    void lastWolfDyingTakesPrecedence() {
        WerewolfEngine engine = newEngine(Board.WEREWOLF_8);
        WerewolfState state = engine.getState();

        // 造一个"平民死光 + 狼也死光"的局面
        byRole(engine, Role.VILLAGER).forEach(p -> p.die(WerewolfEngine.DEATH_VOTE));
        state.aliveWolves().forEach(w -> w.die(WerewolfEngine.DEATH_HUNTER));

        assertTrue(engine.evaluateWinConditionNow(), "狼全死了，判定应该直接结束对局");

        assertEquals(Camp.GOOD.name(), state.getWinnerCamp(),
                "狼先死光就该判好人赢，不能因为平民也没了就算狼赢");
    }

    /**
     * 跑完一整夜并结算，用来到达"会做胜负判定"的那一步。
     *
     * <p>之所以要跑一夜：胜负判定挂在死亡结算之后，光把人的 alive 置 false 不会触发。</p>
     */
    private void startNightAndSettle(WerewolfEngine engine) {
        WerewolfState state = engine.getState();
        // 如果已经结束了就别再动
        if (state.getPhase() == GamePhase.FINISHED) {
            return;
        }
        // 直接推进：狼刀 → 女巫 → 预言家（活着的角色才行动）
        List<WerewolfPlayer> wolves = state.aliveWolves();
        List<WerewolfPlayer> targets = state.alivePlayers().stream()
                .filter(p -> !p.isWolf()).toList();
        if (!wolves.isEmpty() && !targets.isEmpty()) {
            int victim = targets.get(0).getSeatNo();
            for (WerewolfPlayer wolf : wolves) {
                engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL, victim));
            }
            WerewolfPlayer witch = state.findAliveByRole(Role.WITCH);
            if (witch != null && engine.optionsFor(witch.getSeatNo()).contains(ActionType.PASS.name())) {
                engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null));
            }
            WerewolfPlayer seer = state.findAliveByRole(Role.SEER);
            if (seer != null && engine.optionsFor(seer.getSeatNo()).contains(ActionType.SEER_CHECK.name())) {
                List<WerewolfPlayer> w = state.aliveWolves();
                if (!w.isEmpty()) {
                    engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK,
                            w.get(0).getSeatNo()));
                }
            }
        }
        // 万一还没判定（比如夜晚没走完就结束了），用超时兜一下
        if (state.getPhase() != GamePhase.FINISHED && engine.isTimeout()) {
            engine.timeout();
        }
        // 注意：这里**不能**断言"一定结束了" —— 该不该结束由调用方的断言决定。
        // 这个方法只负责"把局面推到会做判定的那一步"。
    }
}
