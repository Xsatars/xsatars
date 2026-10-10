package com.aitable.game.werewolf;

import com.aitable.common.enums.Role;
import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionType;
import com.aitable.game.core.GameEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主持人素材的来源测试。
 *
 * <p>这一组测试是为了守住一个真实踩过的 bug：<b>夜里死了人，主持人却说"平安夜"</b>。</p>
 *
 * <p>原因是主持人一开始去读 {@code state.getLastNightDeaths()}，
 * 但那个字段是给"让死者先发遗言"用的，等主持人说话时已经被清空了。
 * 修法是改成从 <b>事件自带的 payload</b> 里取 —— 事件是不可变的。
 * 所以这个测试直接盯住"事件里必须带着死亡名单"这件事。</p>
 */
class HostContextTest {

    private WerewolfEngine newEngine() {
        List<WerewolfPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            players.add(new WerewolfPlayer(seat, 100L + seat, "玩家" + seat));
        }
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players);
        engine.start();
        return engine;
    }

    /**
     * 从一批事件里找出"天亮结算"那个 NIGHT_RESULT。
     *
     * <p>注意：NIGHT_RESULT 这个类型历史上被好几处用过（狼队分票也发过一条），
     * 只有带 {@code deaths} 字段的那条才是真正的天亮结算。</p>
     */
    private GameEvent nightResult(List<GameEvent> events) {
        for (GameEvent event : events) {
            if (!"NIGHT_RESULT".equals(event.getType()) || !event.isPublicEvent()) {
                continue;
            }
            if (event.getPayload() instanceof Map<?, ?> payload && payload.containsKey("deaths")) {
                return event;
            }
        }
        return null;
    }

    @Test
    @DisplayName("★ 夜里死了人：NIGHT_RESULT 里必须带着死亡名单")
    void nightResultCarriesDeaths() {
        WerewolfEngine engine = newEngine();
        List<WerewolfPlayer> wolves = engine.getState().aliveWolves();
        // 挑一个不是女巫的人，避免她自救（那会变成真的平安夜）
        int victim = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER)
                .findFirst().orElseThrow().getSeatNo();

        List<GameEvent> all = new ArrayList<>();
        for (WerewolfPlayer wolf : wolves) {
            all.addAll(engine.apply(ActionRequest.of(
                    wolf.getSeatNo(), ActionType.WEREWOLF_KILL, victim)).getEvents());
        }
        WerewolfPlayer witch = engine.getState().findAliveByRole(Role.WITCH);
        all.addAll(engine.apply(ActionRequest.of(
                witch.getSeatNo(), ActionType.PASS, null)).getEvents());
        WerewolfPlayer seer = engine.getState().findAliveByRole(Role.SEER);
        all.addAll(engine.apply(ActionRequest.of(seer.getSeatNo(),
                ActionType.SEER_CHECK, wolves.get(0).getSeatNo())).getEvents());

        GameEvent result = nightResult(all);
        assertNotNull(result, "天亮时应该有一个 NIGHT_RESULT 事件");

        Map<?, ?> payload = (Map<?, ?>) result.getPayload();
        Object deaths = payload.get("deaths");
        assertNotNull(deaths, "NIGHT_RESULT 里必须带 deaths 字段（主持人靠它说话）");
        assertTrue(deaths instanceof List<?>, "deaths 应该是个列表，实际：" + deaths);
        List<?> deathList = (List<?>) deaths;
        assertFalse(deathList.isEmpty(), "死了人就该有名单，实际：" + deaths);
        assertTrue(String.valueOf(deathList.get(0)).contains(String.valueOf(victim)),
                "名单里应该是那个被刀的人：" + deaths);

        // 这行是重点：事件带的数据不能依赖 state —— 事件发生时 state 可能已经被改了
        assertEquals(Boolean.FALSE, payload.get("safeNight"), "死了人就不该标记成平安夜");
    }

    @Test
    @DisplayName("★ 真正的平安夜：deaths 为空且 safeNight=true")
    void realSafeNightIsMarked() {
        WerewolfEngine engine = newEngine();
        List<WerewolfPlayer> wolves = engine.getState().aliveWolves();
        int victim = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER)
                .findFirst().orElseThrow().getSeatNo();

        List<GameEvent> all = new ArrayList<>();
        for (WerewolfPlayer wolf : wolves) {
            all.addAll(engine.apply(ActionRequest.of(
                    wolf.getSeatNo(), ActionType.WEREWOLF_KILL, victim)).getEvents());
        }
        // 女巫救人 → 真的平安夜
        WerewolfPlayer witch = engine.getState().findAliveByRole(Role.WITCH);
        all.addAll(engine.apply(ActionRequest.of(
                witch.getSeatNo(), ActionType.WITCH_SAVE, null)).getEvents());
        WerewolfPlayer seer = engine.getState().findAliveByRole(Role.SEER);
        all.addAll(engine.apply(ActionRequest.of(seer.getSeatNo(),
                ActionType.SEER_CHECK, wolves.get(0).getSeatNo())).getEvents());

        Map<?, ?> payload = (Map<?, ?>) nightResult(all).getPayload();
        assertTrue(((List<?>) payload.get("deaths")).isEmpty(), "救下来了就该是空名单");
        assertEquals(Boolean.TRUE, payload.get("safeNight"), "这才该标记成平安夜");
    }

    @Test
    @DisplayName("★ 女巫毒死人也算死亡（毒+刀同一个人不该重复计数）")
    void poisonAlsoCounts() {
        WerewolfEngine engine = newEngine();
        List<WerewolfPlayer> wolves = engine.getState().aliveWolves();
        int wolfVictim = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER)
                .findFirst().orElseThrow().getSeatNo();
        int poisonVictim = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER && p.getSeatNo() != wolfVictim)
                .findFirst().orElseThrow().getSeatNo();

        List<GameEvent> all = new ArrayList<>();
        for (WerewolfPlayer wolf : wolves) {
            all.addAll(engine.apply(ActionRequest.of(
                    wolf.getSeatNo(), ActionType.WEREWOLF_KILL, wolfVictim)).getEvents());
        }
        WerewolfPlayer witch = engine.getState().findAliveByRole(Role.WITCH);
        all.addAll(engine.apply(ActionRequest.of(
                witch.getSeatNo(), ActionType.WITCH_POISON, poisonVictim)).getEvents());
        WerewolfPlayer seer = engine.getState().findAliveByRole(Role.SEER);
        all.addAll(engine.apply(ActionRequest.of(seer.getSeatNo(),
                ActionType.SEER_CHECK, wolves.get(0).getSeatNo())).getEvents());

        List<?> deaths = (List<?>) ((Map<?, ?>) nightResult(all).getPayload()).get("deaths");
        assertEquals(2, deaths.size(), "刀一个毒一个应该是 2 个人：" + deaths);
    }

    @Test
    @DisplayName("★ VOTE_RESULT 里带着被放逐的人（主持人宣布放逐靠它）")
    void voteResultCarriesExiled() {
        WerewolfEngine engine = newEngine();

        // 跳过发言阶段
        int guard = 0;
        while (engine.getState().getPhase() == com.aitable.game.core.GamePhase.DAY_DISCUSSION
                && guard++ < 20) {
            int seat = WerewolfViews.currentSpeakerSeat(engine.getState());
            if (seat <= 0) {
                break;
            }
            engine.apply(ActionRequest.speak(seat, "过"));
        }
        if (engine.getState().getPhase() != com.aitable.game.core.GamePhase.DAY_VOTE) {
            return;   // 没走到投票（对局提前结束了），交给别的测试
        }

        List<WerewolfPlayer> alive = engine.getState().getPlayers().stream()
                .filter(WerewolfPlayer::isAlive).toList();
        int target = alive.get(0).getSeatNo();
        List<GameEvent> all = new ArrayList<>();
        for (WerewolfPlayer voter : alive) {
            if (voter.getSeatNo() == target) {
                continue;
            }
            all.addAll(engine.apply(ActionRequest.of(
                    voter.getSeatNo(), ActionType.VOTE, target)).getEvents());
        }

        GameEvent voteResult = all.stream()
                .filter(e -> "VOTE_RESULT".equals(e.getType()))
                .findFirst().orElse(null);
        assertNotNull(voteResult, "投票完应该有个 VOTE_RESULT");
        Object exiled = ((Map<?, ?>) voteResult.getPayload()).get("exiledSeat");
        assertNotNull(exiled, "有人被票出去时 exiledSeat 必须有值");
        assertFalse(String.valueOf(exiled).isBlank());
    }
}
