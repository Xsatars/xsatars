package com.aitable.game.werewolf;

import com.aitable.common.enums.Role;
import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionResult;
import com.aitable.game.core.GameEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 夜晚播报 + 女巫自救的测试。
 *
 * <p>这两件事都是"玩起来才发现不对"的地方：</p>
 * <ul>
 *   <li>以前女巫被刀不能自救 —— 第一晚就被秒，等于白板</li>
 *   <li>以前夜晚一句话都不播报 —— 出局的人和围观的人完全不知道场上在干什么</li>
 * </ul>
 */
class NightAnnounceTest {

    private WerewolfEngine newEngine() {
        List<WerewolfPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            players.add(new WerewolfPlayer(seat, 100L + seat, "玩家" + seat));
        }
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players);
        engine.start();
        return engine;
    }

    private WerewolfPlayer byRole(WerewolfEngine engine, Role role) {
        return engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == role)
                .findFirst()
                .orElseThrow();
    }

    /** 把一批事件里的公开播报文字摘出来，方便断言和排查 */
    private List<String> publicMessages(List<GameEvent> events) {
        List<String> result = new ArrayList<>();
        for (GameEvent event : events) {
            if (event.isPublicEvent() && event.getMessage() != null) {
                result.add(event.getMessage());
            }
        }
        return result;
    }

    // ============================================================
    // 女巫自救
    // ============================================================

    @Test
    @DisplayName("★ 狼人刀女巫时，女巫可以自救（以前是不允许的）")
    void witchCanSaveHerself() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer witch = byRole(engine, Role.WITCH);
        List<WerewolfPlayer> wolves = engine.getState().aliveWolves();
        assertEquals(2, wolves.size(), "这个板子应该有 2 只狼");

        // 两只狼都刀女巫
        for (WerewolfPlayer wolf : wolves) {
            ActionResult r = engine.apply(ActionRequest.of(
                    wolf.getSeatNo(), com.aitable.game.core.ActionType.WEREWOLF_KILL, witch.getSeatNo()));
            assertTrue(r.isSuccess(), "狼刀失败：" + r.getMessage());
        }
        assertEquals(witch.getSeatNo(), engine.getState().getWolfTarget(), "狼队的目标应该就是女巫");

        // 轮到女巫：她应该能用解药
        assertTrue(engine.optionsFor(witch.getSeatNo()).contains(com.aitable.game.core.ActionType.WITCH_SAVE.name()),
                "女巫被刀时，可行动作里应该包含「用解药」");

        ActionResult save = engine.apply(ActionRequest.of(
                witch.getSeatNo(), com.aitable.game.core.ActionType.WITCH_SAVE, null));
        assertTrue(save.isSuccess(), "自救居然失败了：" + save.getMessage());
        assertTrue(witch.isAlive(), "用了自救之后，女巫应该还活着");
        assertTrue(engine.getState().isWitchSaveUsed(), "解药应该被标记为已用");
    }

    // ============================================================
    // 夜晚播报
    // ============================================================

    @Test
    @DisplayName("★ 开局就播报「狼人请睁眼」")
    void announcesWolfStepOnStart() {
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players());
        List<GameEvent> events = engine.start();

        List<String> msgs = publicMessages(events);
        assertTrue(msgs.stream().anyMatch(m -> m.contains("狼人请睁眼")),
                "开局应该公开播报「狼人请睁眼」，实际播报：" + msgs);
        // 播报还要进游戏日志，否则翻记录的人看不到
        assertTrue(engine.getState().getMessages().stream().anyMatch(m -> m.contains("狼人请睁眼")),
                "播报应该同时进游戏日志，实际日志：" + engine.getState().getMessages());
    }

    @Test
    @DisplayName("★ 夜晚推进到女巫、预言家时也各有播报")
    void announcesEachNightStep() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer witch = byRole(engine, Role.WITCH);
        WerewolfPlayer wolf1 = engine.getState().aliveWolves().get(0);
        WerewolfPlayer wolf2 = engine.getState().aliveWolves().get(1);
        WerewolfPlayer seer = byRole(engine, Role.SEER);

        List<String> all = new ArrayList<>();

        // 两只狼都刀同一个人（不刀女巫，让她能正常出场）
        int victim = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER)
                .findFirst().orElseThrow().getSeatNo();
        all.addAll(publicMessages(engine.apply(ActionRequest.of(
                wolf1.getSeatNo(), com.aitable.game.core.ActionType.WEREWOLF_KILL, victim)).getEvents()));
        all.addAll(publicMessages(engine.apply(ActionRequest.of(
                wolf2.getSeatNo(), com.aitable.game.core.ActionType.WEREWOLF_KILL, victim)).getEvents()));

        // 女巫过（不用药）
        all.addAll(publicMessages(engine.apply(ActionRequest.of(
                witch.getSeatNo(), com.aitable.game.core.ActionType.PASS, null)).getEvents()));

        // 预言家查验
        all.addAll(publicMessages(engine.apply(ActionRequest.of(
                seer.getSeatNo(), com.aitable.game.core.ActionType.SEER_CHECK,
                wolf1.getSeatNo())).getEvents()));

        assertTrue(all.stream().anyMatch(m -> m.contains("女巫请睁眼")),
                "应该播报「女巫请睁眼」，实际：" + all);
        assertTrue(all.stream().anyMatch(m -> m.contains("预言家请睁眼")),
                "应该播报「预言家请睁眼」，实际：" + all);
    }

    @Test
    @DisplayName("★ 播报只说「轮到谁」，绝不泄露狼刀了谁")
    void announcementNeverLeaksTheTarget() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer wolf1 = engine.getState().aliveWolves().get(0);
        WerewolfPlayer wolf2 = engine.getState().aliveWolves().get(1);
        int victim = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == Role.SEER)
                .findFirst().orElseThrow().getSeatNo();

        List<String> msgs = new ArrayList<>();
        msgs.addAll(publicMessages(engine.apply(ActionRequest.of(
                wolf1.getSeatNo(), com.aitable.game.core.ActionType.WEREWOLF_KILL, victim)).getEvents()));
        msgs.addAll(publicMessages(engine.apply(ActionRequest.of(
                wolf2.getSeatNo(), com.aitable.game.core.ActionType.WEREWOLF_KILL, victim)).getEvents()));

        // 播报里不能出现"谁被刀了"这种话
        for (String msg : msgs) {
            assertFalse(msg.contains("被袭击") || msg.contains("今晚的目标"),
                    "公开播报泄露了狼刀目标：" + msg);
        }
    }

    private List<WerewolfPlayer> players() {
        List<WerewolfPlayer> result = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            result.add(new WerewolfPlayer(seat, 100L + seat, "玩家" + seat));
        }
        return result;
    }

    // ============================================================
    // 前端那条"XX 正在行动"的提示条
    // ============================================================

    @Test
    @DisplayName("★ 公开状态会告诉前端『现在轮到哪个角色』")
    void publicStateCarriesNightActor() {
        WerewolfEngine engine = newEngine();

        // 开局就是狼人步
        assertEquals("WOLF", engine.viewFor(1).getPublicState().getNightActor(),
                "开局应该轮到狼人");

        // 狼刀完之后轮到女巫
        List<WerewolfPlayer> wolves = engine.getState().aliveWolves();
        int victim = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER)
                .findFirst().orElseThrow().getSeatNo();
        for (WerewolfPlayer wolf : wolves) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(),
                    com.aitable.game.core.ActionType.WEREWOLF_KILL, victim));
        }
        assertEquals("WITCH", engine.viewFor(1).getPublicState().getNightActor(),
                "狼人行动完应该轮到女巫");

        // 女巫过 → 轮到预言家
        WerewolfPlayer witch = engine.getState().findAliveByRole(Role.WITCH);
        engine.apply(ActionRequest.of(witch.getSeatNo(),
                com.aitable.game.core.ActionType.PASS, null));
        assertEquals("SEER", engine.viewFor(1).getPublicState().getNightActor(),
                "女巫行动完应该轮到预言家");
    }

    @Test
    @DisplayName("★ 提示条只说『轮到谁』，一个目标都不含")
    void nightActorNeverLeaksTarget() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer wolf = engine.getState().aliveWolves().get(0);
        int victim = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == Role.SEER)
                .findFirst().orElseThrow().getSeatNo();
        engine.apply(ActionRequest.of(wolf.getSeatNo(),
                com.aitable.game.core.ActionType.WEREWOLF_KILL, victim));

        // nightActor 只是个角色代号，不该带数字或人名
        String actor = engine.viewFor(1).getPublicState().getNightActor();
        assertEquals("WOLF", actor);
        assertFalse(actor.matches(".*\\d.*"), "提示内容不能含座位号");
    }

    @Test
    @DisplayName("★ 白天没有 nightActor（提示条只该在夜晚出现）")
    void noNightActorAfterNight() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer witch = byRole(engine, Role.WITCH);
        WerewolfPlayer seer = byRole(engine, Role.SEER);
        List<WerewolfPlayer> wolves = engine.getState().aliveWolves();
        int victim = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER)
                .findFirst().orElseThrow().getSeatNo();

        for (WerewolfPlayer wolf : wolves) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(),
                    com.aitable.game.core.ActionType.WEREWOLF_KILL, victim));
        }
        engine.apply(ActionRequest.of(witch.getSeatNo(),
                com.aitable.game.core.ActionType.PASS, null));
        engine.apply(ActionRequest.of(seer.getSeatNo(),
                com.aitable.game.core.ActionType.SEER_CHECK, wolves.get(0).getSeatNo()));

        assertEquals(com.aitable.game.core.GamePhase.DAY_DISCUSSION, engine.getState().getPhase(),
                "三夜走完应该天亮了");
        assertNull(engine.viewFor(1).getPublicState().getNightActor(),
                "白天不该有『谁正在行动』的提示");
    }
}
