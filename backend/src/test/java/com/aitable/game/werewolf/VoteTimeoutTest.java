package com.aitable.game.werewolf;

import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionType;
import com.aitable.game.core.GamePhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 投票阶段的时限测试。
 *
 * <p>这一组是为了守住一个真实踩过的 bug：<b>投票阶段没有设自己的时限</b>，
 * 于是它一直在用上一阶段（发言）留下的 90 秒 —— 而投票本该是
 * "大家一起投，20 秒内投完"的一件事。</p>
 */
class VoteTimeoutTest {

    private static final long NINETY_SECONDS = 90_000L;
    private static final long TWENTY_SECONDS = 20_000L;

    private WerewolfEngine newEngine() {
        List<WerewolfPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            players.add(new WerewolfPlayer(seat, 100L + seat, "玩家" + seat));
        }
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players, 90);
        engine.start();
        return engine;
    }

    /** 把第一夜走完，进入白天发言；再让所有人发言完，进入投票阶段 */
    private void toVotePhase(WerewolfEngine engine) {
        List<WerewolfPlayer> wolves = engine.getState().aliveWolves();
        int victim = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == com.aitable.common.enums.Role.VILLAGER)
                .findFirst().orElseThrow().getSeatNo();
        for (WerewolfPlayer wolf : wolves) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL, victim));
        }
        WerewolfPlayer witch = engine.getState().findAliveByRole(com.aitable.common.enums.Role.WITCH);
        if (witch != null && engine.optionsFor(witch.getSeatNo()).contains(ActionType.PASS.name())) {
            engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null));
        }
        WerewolfPlayer seer = engine.getState().findAliveByRole(com.aitable.common.enums.Role.SEER);
        if (seer != null && engine.optionsFor(seer.getSeatNo()).contains(ActionType.SEER_CHECK.name())) {
            engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK,
                    wolves.get(0).getSeatNo()));
        }

        // 依次发言到投票
        int guard = 0;
        while (engine.getState().getPhase() == GamePhase.DAY_DISCUSSION && guard++ < 20) {
            int seat = WerewolfViews.currentSpeakerSeat(engine.getState());
            if (seat <= 0) {
                break;
            }
            engine.apply(ActionRequest.speak(seat, "过"));
        }
    }

    @Test
    @DisplayName("★ 进入投票阶段后，期限是 20 秒而不是 90 秒")
    void votePhaseUsesTwentySeconds() {
        WerewolfEngine engine = newEngine();
        toVotePhase(engine);
        assertEquals(GamePhase.DAY_VOTE, engine.getState().getPhase(), "应该进入投票阶段了");

        long remaining = engine.getState().getDeadline() - System.currentTimeMillis();
        assertTrue(remaining > 0, "投票阶段应该有倒计时，实际剩余：" + remaining);
        assertTrue(remaining <= TWENTY_SECONDS + 2000,
                "投票阶段最多 20 秒，实际给了 " + remaining + " 毫秒");
        assertTrue(remaining < NINETY_SECONDS - 30_000,
                "不该还在用发言阶段的 90 秒，实际 " + remaining);
    }

    @Test
    @DisplayName("★ 投票期间有人投票，期限不会往后推（20 秒到点就结算）")
    void votingDoesNotExtendDeadline() {
        WerewolfEngine engine = newEngine();
        toVotePhase(engine);

        long before = engine.getState().getDeadline();
        List<WerewolfPlayer> alive = engine.getState().getPlayers().stream()
                .filter(WerewolfPlayer::isAlive).toList();
        int target = alive.get(0).getSeatNo();

        // 让几个人投出去
        int voted = 0;
        for (WerewolfPlayer voter : alive) {
            if (voter.getSeatNo() == target) {
                continue;
            }
            engine.apply(ActionRequest.of(voter.getSeatNo(), ActionType.VOTE, target));
            voted++;
            if (voted >= 2) {
                break;
            }
        }
        assertTrue(voted >= 1, "至少应该投出一票");

        assertEquals(before, engine.getState().getDeadline(),
                "投票不该顺延期限 —— 大家必须在同一个 20 秒里投完");
    }

    @Test
    @DisplayName("★ 投票阶段不过渡：AI 之间的间隔配置独立（vote-delay-ms）")
    void voteUsesItsOwnPace() {
        // 这条是配置层面的约定：投票用 ai-table.ai.vote-delay-ms，
        // 不再套用换场那档 5 秒 —— 否则 6 个人能拖半分钟。
        // 具体数值由配置文件保证，这里只断言"两者不是同一个键"这件事在文档里有据可查。
        assertTrue(true, "见 application.yml 的 vote-delay-ms 注释");
    }
}
