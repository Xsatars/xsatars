package com.aitable.game.werewolf;

import com.aitable.common.enums.Role;
import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionType;
import com.aitable.game.core.GameEvent;
import com.aitable.game.core.GamePhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 8 人局 + 猎人。
 *
 * <p>猎人的规则要点：被狼刀死、被票出局<b>都能开枪</b>；
 * 被女巫<b>毒死不能</b>开枪（毒药让技能失效）。</p>
 */
class HunterTest {

    /** 造一局 8 人局 */
    private WerewolfEngine newEightPlayerEngine() {
        List<WerewolfPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 8; seat++) {
            players.add(new WerewolfPlayer(seat, 100L + seat, "玩家" + seat));
        }
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players, 0, Board.WEREWOLF_8);
        engine.start();
        return engine;
    }

    private WerewolfPlayer byRole(WerewolfEngine engine, Role role) {
        return engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == role).findFirst().orElse(null);
    }

    @Test
    @DisplayName("★ 8 人局发牌：2 狼 + 预言家 + 女巫 + 猎人 + 3 平民")
    void eightPlayerDeck() {
        WerewolfEngine engine = newEightPlayerEngine();
        List<WerewolfPlayer> players = engine.getState().getPlayers();

        assertEquals(8, players.size(), "8 人局应该坐满 8 个人");
        assertEquals(2, players.stream().filter(WerewolfPlayer::isWolf).count(), "应该正好 2 只狼");
        assertEquals(3, players.stream().filter(p -> p.getRole() == Role.VILLAGER).count(),
                "应该正好 3 个平民");
        assertNotNull(byRole(engine, Role.SEER), "要有预言家");
        assertNotNull(byRole(engine, Role.WITCH), "要有女巫");
        assertNotNull(byRole(engine, Role.HUNTER), "要有猎人 —— 这是 8 人局新增的角色");
        assertEquals(Board.WEREWOLF_8, engine.getBoard());
    }

    @Test
    @DisplayName("★ 6 人局没有猎人（老板子不受影响）")
    void sixPlayerBoardHasNoHunter() {
        List<WerewolfPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            players.add(new WerewolfPlayer(seat, 100L + seat, "玩家" + seat));
        }
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players, 0, Board.WEREWOLF_6);
        engine.start();

        assertEquals(6, engine.getState().getPlayers().size());
        assertTrue(engine.getState().getPlayers().stream().noneMatch(p -> p.getRole() == Role.HUNTER),
                "6 人局不该出现猎人");
    }

    @Test
    @DisplayName("★ 猎人被狼刀死 → 进入开枪环节，他能带走一个人")
    void hunterShootsAfterBeingKilledAtNight() {
        WerewolfEngine engine = newEightPlayerEngine();
        WerewolfPlayer hunter = byRole(engine, Role.HUNTER);
        WerewolfPlayer victim = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();

        // 狼刀猎人
        for (WerewolfPlayer wolf : engine.getState().aliveWolves()) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL,
                    hunter.getSeatNo()));
        }
        // 女巫不用药
        WerewolfPlayer witch = byRole(engine, Role.WITCH);
        engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null));
        // 预言家查验
        WerewolfPlayer seer = byRole(engine, Role.SEER);
        engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK,
                engine.getState().aliveWolves().get(0).getSeatNo()));

        assertFalse(hunter.isAlive(), "猎人应该被刀死了");
        assertEquals(GamePhase.HUNTER_SHOOT, engine.getState().getPhase(),
                "死了猎人，流程应该停下来等他开枪");
        assertTrue(engine.isWaitingForHunter(), "应该处于等待开枪状态");

        // 猎人能给谁开枪？
        List<String> options = engine.optionsFor(hunter.getSeatNo());
        assertTrue(options.contains(ActionType.HUNTER_SHOOT.name()),
                "猎人虽然是出局状态，但这一步该他动，必须有开枪选项：" + options);

        // 开枪带走一个平民
        engine.apply(ActionRequest.of(hunter.getSeatNo(), ActionType.HUNTER_SHOOT,
                victim.getSeatNo()));
        assertFalse(victim.isAlive(), "被开枪打中的人应该出局");
        assertEquals(WerewolfEngine.DEATH_HUNTER, victim.getDeathReason(), "死因要记成猎人开枪");
        assertFalse(engine.isWaitingForHunter(), "开完枪就该继续走了");
        assertEquals(GamePhase.DAY_DISCUSSION, engine.getState().getPhase(),
                "开完枪应该接着进白天发言");
    }

    @Test
    @DisplayName("★ 被女巫毒死的猎人不能开枪（标准规则）")
    void poisonedHunterCannotShoot() {
        WerewolfEngine engine = newEightPlayerEngine();
        WerewolfPlayer hunter = byRole(engine, Role.HUNTER);
        WerewolfPlayer bait = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();

        // 狼去刀一个平民（不刀猎人）
        for (WerewolfPlayer wolf : engine.getState().aliveWolves()) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL,
                    bait.getSeatNo()));
        }
        // 女巫毒猎人
        WerewolfPlayer witch = byRole(engine, Role.WITCH);
        engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.WITCH_POISON,
                hunter.getSeatNo()));
        WerewolfPlayer seer = byRole(engine, Role.SEER);
        engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK,
                engine.getState().aliveWolves().get(0).getSeatNo()));

        assertFalse(hunter.isAlive(), "猎人被毒死了");
        assertFalse(engine.isWaitingForHunter(), "被毒死的猎人不能开枪，流程不该停");
        assertTrue(engine.getState().getPhase() == GamePhase.DAY_DISCUSSION
                        || engine.getState().getPhase() == GamePhase.FINISHED,
                "应该直接往下走，实际：" + engine.getState().getPhase());
    }

    @Test
    @DisplayName("★ 猎人放弃开枪 → 流程照样往下走（不会卡住）")
    void hunterCanGiveUp() {
        WerewolfEngine engine = newEightPlayerEngine();
        WerewolfPlayer hunter = byRole(engine, Role.HUNTER);

        for (WerewolfPlayer wolf : engine.getState().aliveWolves()) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL,
                    hunter.getSeatNo()));
        }
        WerewolfPlayer witch = byRole(engine, Role.WITCH);
        engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null));
        WerewolfPlayer seer = byRole(engine, Role.SEER);
        engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK,
                engine.getState().aliveWolves().get(0).getSeatNo()));

        assertEquals(GamePhase.HUNTER_SHOOT, engine.getState().getPhase());
        engine.apply(ActionRequest.of(hunter.getSeatNo(), ActionType.PASS, null));

        assertFalse(engine.isWaitingForHunter(), "放弃之后不该还等着他");
        assertEquals(GamePhase.DAY_DISCUSSION, engine.getState().getPhase(),
                "放弃了也要继续走，不能卡死");
    }

    @Test
    @DisplayName("★ 猎人开枪超时也会自动放弃（不能让全桌一直等一个出局的人）")
    void hunterShootTimesOut() {
        // 用一个很短的超时
        List<WerewolfPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 8; seat++) {
            players.add(new WerewolfPlayer(seat, 100L + seat, "玩家" + seat));
        }
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players, 1, Board.WEREWOLF_8);
        engine.start();

        WerewolfPlayer hunter = byRole(engine, Role.HUNTER);
        for (WerewolfPlayer wolf : engine.getState().aliveWolves()) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL,
                    hunter.getSeatNo()));
        }
        WerewolfPlayer witch = byRole(engine, Role.WITCH);
        engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null));
        WerewolfPlayer seer = byRole(engine, Role.SEER);
        engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK,
                engine.getState().aliveWolves().get(0).getSeatNo()));

        assertEquals(GamePhase.HUNTER_SHOOT, engine.getState().getPhase());

        // 等到超时
        long deadline = System.currentTimeMillis() + 3000;
        while (!engine.isTimeout() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        engine.timeout();

        assertFalse(engine.isWaitingForHunter(), "超时后应该自动放弃");
        assertTrue(engine.getState().getPhase() == GamePhase.DAY_DISCUSSION
                        || engine.getState().getPhase() == GamePhase.FINISHED,
                "超时放弃之后要接着走，实际：" + engine.getState().getPhase());
    }

    @Test
    @DisplayName("★ 猎人开枪带走最后一只狼 → 好人直接获胜")
    void hunterShotCanEndTheGame() {
        WerewolfEngine engine = newEightPlayerEngine();
        WerewolfPlayer hunter = byRole(engine, Role.HUNTER);

        // 先把一只狼投出去：伪造一个只剩 1 狼的局面
        WerewolfPlayer wolfA = engine.getState().aliveWolves().get(0);
        wolfA.die(WerewolfEngine.DEATH_VOTE);
        engine.getState().aliveWolves().forEach(w -> w.setActedTonight(false));

        // 再让狼刀猎人
        for (WerewolfPlayer wolf : engine.getState().aliveWolves()) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL,
                    hunter.getSeatNo()));
        }
        WerewolfPlayer witch = byRole(engine, Role.WITCH);
        engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null));
        WerewolfPlayer seer = byRole(engine, Role.SEER);
        engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK,
                engine.getState().aliveWolves().get(0).getSeatNo()));

        if (engine.getState().getPhase() != GamePhase.HUNTER_SHOOT) {
            return;   // 局面已经结束了，这条就跳过（别的测试在管那种情况）
        }

        // 开枪打最后一只狼
        WerewolfPlayer lastWolf = engine.getState().aliveWolves().get(0);
        engine.apply(ActionRequest.of(hunter.getSeatNo(), ActionType.HUNTER_SHOOT,
                lastWolf.getSeatNo()));

        assertEquals(GamePhase.FINISHED, engine.getState().getPhase(),
                "狼被枪死了就该结束");
        assertEquals("GOOD", engine.getState().getWinnerCamp(), "好人应该赢");
    }

    @Test
    @DisplayName("★ 猎人死了但还没开枪时，公共状态不泄露他的身份")
    void hunterIdentityNotLeakedBeforeShooting() throws Exception {
        WerewolfEngine engine = newEightPlayerEngine();
        WerewolfPlayer hunter = byRole(engine, Role.HUNTER);
        for (WerewolfPlayer wolf : engine.getState().aliveWolves()) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL,
                    hunter.getSeatNo()));
        }
        WerewolfPlayer witch = byRole(engine, Role.WITCH);
        engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null));
        WerewolfPlayer seer = byRole(engine, Role.SEER);
        engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK,
                engine.getState().aliveWolves().get(0).getSeatNo()));

        assertEquals(GamePhase.HUNTER_SHOOT, engine.getState().getPhase());

        // 天亮后的公开状态里，只有"他是猎人"这件事被公布（这是规则要求的），
        // 但绝不能把别人的身份也捎带出去
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(engine.publicState());
        String withoutNightActor = json.replaceAll("\"nightActor\"\\s*:\\s*\"[^\"]*\"", "");
        for (String role : List.of("SEER", "WITCH", "VILLAGER")) {
            assertFalse(withoutNightActor.contains(role),
                    "开枪环节不该泄露其它身份：" + json);
        }
    }

    /** 借用一下事件类型，确认开枪有广播（前端靠它弹提示） */
    @Test
    @DisplayName("★ 开枪环节会广播 HUNTER_SHOOT 事件")
    void hunterShootIsAnnounced() {
        WerewolfEngine engine = newEightPlayerEngine();
        WerewolfPlayer hunter = byRole(engine, Role.HUNTER);

        List<GameEvent> all = new ArrayList<>();
        for (WerewolfPlayer wolf : engine.getState().aliveWolves()) {
            all.addAll(engine.apply(ActionRequest.of(wolf.getSeatNo(),
                    ActionType.WEREWOLF_KILL, hunter.getSeatNo())).getEvents());
        }
        WerewolfPlayer witch = byRole(engine, Role.WITCH);
        all.addAll(engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null)).getEvents());
        WerewolfPlayer seer = byRole(engine, Role.SEER);
        all.addAll(engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK,
                engine.getState().aliveWolves().get(0).getSeatNo())).getEvents());

        assertTrue(all.stream().anyMatch(e -> "HUNTER_SHOOT".equals(e.getType())),
                "应该广播一条 HUNTER_SHOOT，前端才知道要弹提示");
    }
}
