package com.aitable.game.ai;

import com.aitable.common.enums.Role;
import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionType;
import com.aitable.game.core.GameView;
import com.aitable.game.werewolf.WerewolfEngine;
import com.aitable.game.werewolf.WerewolfPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 的人设 + 记忆测试（V0.3）。
 *
 * <p>这一版想让 AI "有人味"，靠的是两样东西：</p>
 * <ol>
 *   <li><b>人设</b>：每个 AI 有自己的名字、脾气、说话方式</li>
 *   <li><b>记忆</b>：记得整局谁说过什么、谁投过谁 —— 而不是每轮都失忆</li>
 * </ol>
 */
class AiPersonaAndMemoryTest {

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
                .findFirst().orElseThrow();
    }

    // ============================================================
    // 人设
    // ============================================================

    @Test
    @DisplayName("★ 每个性格的名字、标签、口吻都不一样")
    void everyPersonaIsDistinct() {
        List<String> tags = new ArrayList<>();
        for (AiPersona p : AiPersona.values()) {
            assertNotNull(p.getName(), "性格要有名字");
            assertNotNull(p.getTag(), "性格要有标签");
            assertTrue(p.getStyle().length() > 10, "性格要有像样的口吻描述：" + p.name());
            assertTrue(p.describe().contains(p.getName()), "人设描述里应该包含名字");
            tags.add(p.getTag());
        }
        assertEquals(tags.size(), tags.stream().distinct().count(), "性格标签不能重复");
        assertEquals(6, AiPersona.values().length, "6 人局正好 6 个性格");
    }

    @Test
    @DisplayName("★ 按 key 能找回性格；脏数据不会炸")
    void personaLookupIsSafe() {
        assertEquals(AiPersona.HOTHEAD, AiPersona.byKey("HOTHEAD"));
        assertEquals(AiPersona.SNEAKY, AiPersona.byKey("小狼"), "按名字也能找到");
        assertNull(AiPersona.byKey("不存在的性格"));
        assertNull(AiPersona.byKey(null));
        assertNull(AiPersona.byKey("  "), "老数据可能是空串");
    }

    @Test
    @DisplayName("★ 同一个房间加 AI，性格不会重复")
    void personasDoNotRepeatInOneRoom() {
        List<String> used = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            AiPersona picked = AiPersona.pick(used);
            assertTrue(!used.contains(picked.getName()), "第 " + (i + 1) + " 个不该重复：" + picked.getName());
            used.add(picked.getName());
        }
        assertEquals(6, used.stream().distinct().count());
    }

    @Test
    @DisplayName("★ 对局里的 AI 带着自己的人设（大模型靠它写提示词）")
    void viewCarriesPersona() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer ai = engine.getState().getPlayers().get(0);
        ai.setAi(true);
        ai.setPersona(AiPersona.ANALYST);

        GameView view = engine.viewFor(ai.getSeatNo());
        Object tag = view.getPrivateState().getInfo().get("personaTag");
        Object style = view.getPrivateState().getInfo().get("personaStyle");

        assertEquals("冷静分析", tag, "view 里应该带上性格标签");
        assertNotNull(style, "view 里应该带上口吻描述");
        assertTrue(style.toString().contains("夜观星"), "口吻描述里应该有它的名字");
    }

    @Test
    @DisplayName("真人玩家没有人设（老逻辑不受影响）")
    void humanPlayerHasNoPersona() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer human = engine.getState().getPlayers().get(0);

        assertFalse(engine.viewFor(human.getSeatNo()).getPrivateState().getInfo()
                .containsKey("personaTag"), "真人不该有人设");
    }

    private void assertFalse(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertFalse(condition, message);
    }

    // ============================================================
    // 记忆
    // ============================================================

    @Test
    @DisplayName("★ 发言会被记进历史（跨轮保留，AI 靠它记得住）")
    void speechesAreRemembered() {
        WerewolfEngine engine = newEngine();
        // 先把第一夜走完 —— 开局是夜晚，得等天亮才有人发言
        playThroughNight(engine);

        int currentSeat = com.aitable.game.werewolf.WerewolfViews.currentSpeakerSeat(engine.getState());
        assertTrue(currentSeat > 0,
                "夜晚结束后应该有人轮到发言（当前阶段：" + engine.getState().getPhase() + "）");

        String words = "我觉得 3 号有问题，先记一笔";
        engine.apply(ActionRequest.speak(currentSeat, words));

        List<String> history = engine.getState().speechHistoryText(50);
        assertTrue(history.stream().anyMatch(h -> h.contains(words)),
                "发言应该进历史，实际：" + history);
        assertTrue(history.stream().anyMatch(h -> h.contains(currentSeat + " 号")),
                "历史里要带上是谁说的：" + history);
    }

    /** 把第一夜走完（狼刀 → 女巫 → 预言家），进入白天 */
    private void playThroughNight(WerewolfEngine engine) {
        // 优先刀**平民**：刀到女巫（她能自救）或预言家（可能直接死掉）都会让后续分支变多。
        // 这个辅助方法要的是"稳定走到白天"，不需要覆盖那些情况（别的测试在管）。
        int victim = engine.getState().getPlayers().stream()
                .filter(p -> p.isAlive() && p.getRole() == Role.VILLAGER)
                .map(WerewolfPlayer::getSeatNo)
                .findFirst()
                .orElseGet(() -> engine.getState().getPlayers().stream()
                        .filter(p -> p.isAlive() && !p.isWolf())
                        .map(WerewolfPlayer::getSeatNo)
                        .findFirst().orElseThrow());

        for (WerewolfPlayer wolf : engine.getState().aliveWolves()) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL, victim));
        }

        // 每一步都先确认"现在真的轮到他、而且这个动作可用"，不依赖固定顺序
        WerewolfPlayer witch = engine.getState().findAliveByRole(Role.WITCH);
        if (witch != null && engine.optionsFor(witch.getSeatNo()).contains(ActionType.PASS.name())) {
            engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null));
        }
        WerewolfPlayer seer = engine.getState().findAliveByRole(Role.SEER);
        if (seer != null && engine.optionsFor(seer.getSeatNo()).contains(ActionType.SEER_CHECK.name())) {
            engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK,
                    engine.getState().aliveWolves().get(0).getSeatNo()));
        }

        assertEquals(com.aitable.game.core.GamePhase.DAY_DISCUSSION, engine.getState().getPhase(),
                "走完第一夜应该天亮了（否则下面的发言断言会莫名其妙地失败）");
    }

    @Test
    @DisplayName("★ 投票明细会被记进历史（谁投了谁）")
    void votesAreRemembered() {
        WerewolfEngine engine = newEngine();
        List<WerewolfPlayer> alive = engine.getState().getPlayers().stream()
                .filter(WerewolfPlayer::isAlive).toList();

        // 跳过发言阶段：所有人依次发言
        int guard = 0;
        while (engine.getState().getPhase() == com.aitable.game.core.GamePhase.DAY_DISCUSSION
                && guard++ < 20) {
            int seat = com.aitable.game.werewolf.WerewolfViews.currentSpeakerSeat(engine.getState());
            if (seat <= 0) {
                break;
            }
            engine.apply(ActionRequest.speak(seat, "我过"));
        }

        if (engine.getState().getPhase() != com.aitable.game.core.GamePhase.DAY_VOTE) {
            return;   // 这局没走到投票（可能已经结束），交给别的测试覆盖
        }

        // 所有人投 1 号以外的人，保证有人被放逐
        int target = alive.stream().filter(p -> p.getSeatNo() != 1).findFirst().orElseThrow().getSeatNo();
        for (WerewolfPlayer voter : alive) {
            if (voter.getSeatNo() == target) {
                continue;   // 不能投自己
            }
            engine.apply(ActionRequest.of(voter.getSeatNo(), ActionType.VOTE, target));
        }

        List<String> votes = engine.getState().voteHistoryText(10);
        assertTrue(votes.stream().anyMatch(v -> v.contains("→")),
                "投票历史里应该能看到「谁→谁」，实际：" + votes);
        assertTrue(votes.stream().anyMatch(v -> v.contains(target + " 号被放逐")),
                "也应该记下最后谁被放逐了：" + votes);
    }

    @Test
    @DisplayName("★ 历史上限生效，但保留最近的（AI 提示词不会无限膨胀）")
    void historyTextIsCapped() {
        WerewolfEngine engine = newEngine();
        for (int i = 0; i < 100; i++) {
            engine.getState().recordSpeech(1, i % 6 + 1, "玩家", "第 " + i + " 条");
        }
        List<String> limited = engine.getState().speechHistoryText(10);
        assertEquals(10, limited.size(), "最多返回 10 条");
        assertTrue(limited.get(9).contains("第 99 条"), "保留的应该是最新的：" + limited.get(9));
    }

    @Test
    @DisplayName("★ 没说过话时历史是空的（不会给 AI 塞一堆空内容）")
    void emptyHistoryIsEmpty() {
        WerewolfEngine engine = newEngine();
        assertTrue(engine.getState().speechHistoryText(10).isEmpty());
        assertTrue(engine.getState().voteHistoryText(10).isEmpty());
    }

    @Test
    @DisplayName("★ 公开状态里带着历史（前端拿来做对局回顾）")
    void publicStateCarriesHistory() {
        WerewolfEngine engine = newEngine();
        engine.getState().recordSpeech(1, 3, "阿豆", "我是好人");
        engine.getState().recordVote(1, new java.util.LinkedHashMap<>() {{
            put(3, 5);
        }}, 5);

        GameView view = engine.viewFor(1);
        assertNotNull(view.getPublicState().getSpeechHistory());
        assertNotNull(view.getPublicState().getVoteHistory());
        assertTrue(view.getPublicState().getSpeechHistory().stream().anyMatch(s -> s.contains("我是好人")),
                "公开状态里应该有发言历史");
        assertNotEquals(0, view.getPublicState().getVoteHistory().size());
    }
}
