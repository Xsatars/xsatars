package com.aitable.game.werewolf;

import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionType;
import com.aitable.game.core.GameView;
import com.aitable.game.core.PublicGameState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态版本号（{@code stateSeq}）。
 *
 * <p><b>为什么需要它</b>：前端同时从两个 STOMP 频道收状态 ——
 * 房间频道收公开事件（{@code PHASE_CHANGE} 里带一份完整公开状态），
 * 用户频道收全量状态（{@code GAME_STATE_UPDATE}）。
 * STOMP <b>不保证跨频道的到达顺序</b>，"新的先到、旧的后到"完全可能发生，
 * 旧快照会把新状态覆盖回去 —— 玩家看到的就是
 * <b>"明明是白天，闪一下狼人正在刀人"</b>。</p>
 *
 * <p>所以每个快照都要带一个只增不减的版本号，前端据此丢掉迟到的旧快照。
 * 这一组测试守的就是"这个号真的在递增、而且每份快照都带上了"。</p>
 */
class StateSeqTest {

    private WerewolfEngine newEngine() {
        List<WerewolfPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            players.add(new WerewolfPlayer(seat, 100L + seat, "玩家" + seat));
        }
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players, 0);
        engine.start();
        return engine;
    }

    @Test
    @DisplayName("★ 每推进一次状态，版本号就变大（只增不减）")
    void seqIncreasesOnEveryAction() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();

        long before = state.getStateSeq();
        for (WerewolfPlayer wolf : state.aliveWolves()) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL, 3));
        }
        long afterKill = state.getStateSeq();
        assertTrue(afterKill > before, "狼刀之后版本号应该变大：" + before + " → " + afterKill);

        WerewolfPlayer witch = state.findAliveByRole(com.aitable.common.enums.Role.WITCH);
        if (witch != null && engine.optionsFor(witch.getSeatNo()).contains(ActionType.PASS.name())) {
            engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null));
        }
        assertTrue(state.getStateSeq() >= afterKill, "版本号绝不能回退");
    }

    @Test
    @DisplayName("★ 公开状态里带着版本号（前端靠它判断新旧）")
    void publicStateCarriesSeq() {
        WerewolfEngine engine = newEngine();
        PublicGameState pub = engine.publicState();
        assertNotNull(pub, "公开状态不该为空");
        assertEquals(engine.getState().getStateSeq(), pub.getStateSeq(),
                "公开状态里的版本号应该等于当前状态版本号");
    }

    @Test
    @DisplayName("★ 每个座位的视图也都带版本号（GAME_STATE_UPDATE 走这条路）")
    void everySeatViewCarriesSeq() {
        WerewolfEngine engine = newEngine();
        long seq = engine.getState().getStateSeq();
        for (WerewolfPlayer player : engine.getState().getPlayers()) {
            GameView view = engine.viewFor(player.getSeatNo());
            assertEquals(seq, view.getPublicState().getStateSeq(),
                    player.getSeatNo() + " 号位的视图版本号不对");
        }
    }

    @Test
    @DisplayName("★ 同一时刻生成的多份快照版本号相同（不会因为生成两次就跳号）")
    void snapshotsAtSameMomentShareSeq() {
        WerewolfEngine engine = newEngine();
        // 模拟"同一批事件里生成了 PHASE_CHANGE 和 GAME_STATE_UPDATE 两份快照"
        long a = engine.publicState().getStateSeq();
        long b = engine.viewFor(1).getPublicState().getStateSeq();
        assertEquals(a, b, "同一时刻的快照版本号必须一致，否则前端会把它们当成新旧两份");
    }

    @Test
    @DisplayName("★ 超时推进也算一次状态变化（版本号也要涨）")
    void timeoutAlsoBumpsSeq() {
        List<WerewolfPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            players.add(new WerewolfPlayer(seat, 100L + seat, "玩家" + seat));
        }
        // 超时 1 秒，方便触发
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players, 1);
        engine.start();

        long deadline = System.currentTimeMillis() + 3000;
        while (!engine.isTimeout() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        long before = engine.getState().getStateSeq();
        engine.timeout();
        assertTrue(engine.getState().getStateSeq() > before,
                "超时推进也要涨版本号，否则前端会把这份快照当重复消息丢掉");
    }

    @Test
    @DisplayName("★ 夜晚时 nightActor 有值、白天时是 null（提示条靠它显示/消失）")
    void nightActorOnlyDuringNight() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();

        // 开局是夜晚：应该有值
        assertEquals(com.aitable.game.core.GamePhase.NIGHT, state.getPhase());
        assertNotNull(engine.publicState().getNightActor(), "夜晚应该有行动人提示");

        // 走完一夜 → 进白天：必须变成 null
        for (WerewolfPlayer wolf : state.aliveWolves()) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL, 3));
        }
        WerewolfPlayer witch = state.findAliveByRole(com.aitable.common.enums.Role.WITCH);
        if (witch != null && engine.optionsFor(witch.getSeatNo()).contains(ActionType.PASS.name())) {
            engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null));
        }
        WerewolfPlayer seer = state.findAliveByRole(com.aitable.common.enums.Role.SEER);
        if (seer != null && engine.optionsFor(seer.getSeatNo())
                .contains(ActionType.SEER_CHECK.name())) {
            engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK, 1));
        }

        if (state.getPhase() != com.aitable.game.core.GamePhase.FINISHED) {
            assertTrue(state.getPhase() != com.aitable.game.core.GamePhase.NIGHT,
                    "走完一夜应该不在夜晚了，实际：" + state.getPhase());
            assertEquals(null, engine.publicState().getNightActor(),
                    "不是夜晚就不能再带行动人提示，否则界面会一直挂着'预言家正在行动'");
        }
    }
}
