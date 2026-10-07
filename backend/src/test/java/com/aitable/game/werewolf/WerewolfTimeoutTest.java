package com.aitable.game.werewolf;

import com.aitable.common.enums.Camp;
import com.aitable.common.enums.Role;
import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionResult;
import com.aitable.game.core.ActionType;
import com.aitable.game.core.GameEvent;
import com.aitable.game.core.GamePhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 超时机制测试。
 *
 * <p>不等真实时间：直接把截止时间改成"已经过去了"，然后调用 {@code timeout()}，
 * 就能验证"玩家挂机时对局会怎么继续"。</p>
 */
class WerewolfTimeoutTest {

    private WerewolfEngine newEngine() {
        return newEngine(WerewolfEngine.DEFAULT_TIMEOUT_SECONDS);
    }

    private WerewolfEngine newEngine(int timeoutSeconds) {
        List<WerewolfPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            players.add(new WerewolfPlayer(seat, 100L + seat, "玩家" + seat));
        }
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players, timeoutSeconds);
        engine.start();
        return engine;
    }

    /** 手动把截止时间改成"已经过时"，模拟等太久 */
    private void forceTimeout(WerewolfEngine engine) {
        engine.getState().setDeadline(System.currentTimeMillis() - 1);
    }

    /**
     * 让整个夜晚超时走完。
     *
     * <p>夜晚按「狼人 → 女巫 → 预言家」分三步，每步各自计时，
     * 所以一次超时只跳过当前这一步，这里一直循环到天亮。</p>
     *
     * @return 这一路上产生的所有事件
     */
    private List<GameEvent> timeoutNight(WerewolfEngine engine) {
        List<GameEvent> all = new ArrayList<>();
        int guard = 0;
        while (engine.phase() == GamePhase.NIGHT && guard++ < 5) {
            forceTimeout(engine);
            ActionResult result = engine.timeout();
            if (result.getEvents() != null) {
                all.addAll(result.getEvents());
            }
        }
        return all;
    }

    private void allWolvesKill(WerewolfEngine engine, int targetSeat) {
        for (WerewolfPlayer wolf : engine.getState().aliveWolves()) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL, targetSeat));
        }
    }

    private void finishDiscussion(WerewolfEngine engine) {
        WerewolfState state = engine.getState();
        int guard = 0;
        while (engine.phase() == GamePhase.DAY_DISCUSSION && guard++ < 20) {
            int index = state.getSpeakIndex();
            if (index >= state.getSpeakOrder().size()) {
                break;
            }
            engine.apply(ActionRequest.speak(state.getSpeakOrder().get(index), "说两句"));
        }
    }

    // ============================================================
    // 截止时间本身
    // ============================================================

    @Test
    @DisplayName("开局后就设置了截止时间，刚开局不算超时")
    void deadlineIsSetAfterStart() {
        WerewolfEngine engine = newEngine();
        assertTrue(engine.getState().getDeadline() > System.currentTimeMillis(), "应该有截止时间");
        assertFalse(engine.isTimeout(), "刚开局不该算超时");
    }

    @Test
    @DisplayName("有人操作会把截止时间往后推")
    void actionPushesDeadlineForward() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer wolf = engine.getState().aliveWolves().get(0);
        WerewolfPlayer victim = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();

        long before = engine.getState().getDeadline();
        engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL, victim.getSeatNo()));
        assertTrue(engine.getState().getDeadline() >= before, "操作后截止时间应该被推后");
        assertFalse(engine.isTimeout());
    }

    @Test
    @DisplayName("把超时时间配成 0 就等于关掉超时机制")
    void zeroTimeoutDisablesDeadline() {
        WerewolfEngine engine = newEngine(0);
        assertEquals(0, engine.getState().getDeadline(), "关闭超时时 deadline 应该是 0");
        assertFalse(engine.isTimeout(), "没有截止时间就不算超时");

        // 玩家操作之后也依然不设置截止时间
        WerewolfPlayer wolf = engine.getState().aliveWolves().get(0);
        WerewolfPlayer victim = engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();
        engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL, victim.getSeatNo()));

        assertEquals(0, engine.getState().getDeadline());
        assertFalse(engine.isTimeout());
    }

    @Test
    @DisplayName("等太久之后 isTimeout 为真")
    void isTimeoutAfterDeadlinePassed() {
        WerewolfEngine engine = newEngine();
        forceTimeout(engine);
        assertTrue(engine.isTimeout());
    }

    // ============================================================
    // 夜晚超时
    // ============================================================

    @Test
    @DisplayName("★ 夜晚没人行动就超时：狼人空刀，直接进入白天，没有人死")
    void nightTimeoutMeansNoKill() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        int aliveBefore = state.alivePlayers().size();

        timeoutNight(engine);

        assertEquals(GamePhase.DAY_DISCUSSION, engine.phase(), "应该直接进入白天");
        assertEquals(aliveBefore, state.alivePlayers().size(), "空刀不该有人死");
        assertTrue(state.getNightDeaths().isEmpty());
    }

    @Test
    @DisplayName("夜晚只投了一半就超时：按已经投出来的票定刀口，那一票依然算数")
    void nightTimeoutUsesAlreadyCastVotes() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        List<WerewolfPlayer> wolves = state.aliveWolves();
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();

        // 只有第一只狼投了票，第二只一直没投
        engine.apply(ActionRequest.of(wolves.get(0).getSeatNo(), ActionType.WEREWOLF_KILL, victim.getSeatNo()));

        timeoutNight(engine);

        assertEquals(GamePhase.DAY_DISCUSSION, engine.phase());
        assertFalse(victim.isAlive(), "已经投出来的那一刀应该生效");
    }

    @Test
    @DisplayName("超时时会通知预言家和女巫：这一步被跳过了")
    void nightTimeoutNotifiesSeerAndWitch() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer seer = state.findAliveByRole(Role.SEER);
        WerewolfPlayer witch = state.findAliveByRole(Role.WITCH);

        List<GameEvent> events = timeoutNight(engine);

        boolean seerNotified = events.stream()
                .anyMatch(e -> !e.isPublicEvent() && Integer.valueOf(seer.getSeatNo()).equals(e.getOwnerSeat()));
        boolean witchNotified = events.stream()
                .anyMatch(e -> !e.isPublicEvent() && Integer.valueOf(witch.getSeatNo()).equals(e.getOwnerSeat()));
        assertTrue(seerNotified, "应该告诉预言家本晚跳过了");
        assertTrue(witchNotified, "应该告诉女巫本晚没用药");
    }

    // ============================================================
    // 发言超时
    // ============================================================

    @Test
    @DisplayName("★ 发言超时：记为「超时未发言」，轮到下一个人")
    void speakTimeoutSkipsCurrentSpeaker() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();

        // 先把夜晚正常走完
        playNight(engine, victim.getSeatNo());
        assertEquals(GamePhase.DAY_DISCUSSION, engine.phase());

        int firstSpeaker = state.getSpeakOrder().get(0);
        assertEquals(firstSpeaker, WerewolfViews.currentSpeakerSeat(state));

        forceTimeout(engine);
        ActionResult result = engine.timeout();
        assertTrue(result.isSuccess());

        GameEvent speakEvent = result.getEvents().stream()
                .filter(e -> "PLAYER_SPEAK".equals(e.getType())).findFirst().orElse(null);
        assertNotNull(speakEvent, "应该产生一条发言记录");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) speakEvent.getPayload();
        assertEquals("（超时未发言）", payload.get("text"));
        assertEquals(Boolean.TRUE, payload.get("timeout"));

        // 轮到下一个人
        assertTrue(WerewolfViews.currentSpeakerSeat(state) != firstSpeaker
                || state.getSpeakIndex() >= state.getSpeakOrder().size());
    }

    @Test
    @DisplayName("所有人都超时没发言 → 自动进入投票阶段")
    void allSpeakTimeoutsLeadToVote() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();

        playNight(engine, victim.getSeatNo());

        int guard = 0;
        while (engine.phase() == GamePhase.DAY_DISCUSSION && guard++ < 10) {
            forceTimeout(engine);
            engine.timeout();
        }
        assertEquals(GamePhase.DAY_VOTE, engine.phase(), "发言都超时后应该进入投票");
    }

    // ============================================================
    // 投票超时
    // ============================================================

    @Test
    @DisplayName("★ 投票超时且一个人都没投 → 本轮无人出局，进入下一夜")
    void voteTimeoutWithNoVotesExilesNobody() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();

        playNight(engine, victim.getSeatNo());
        finishDiscussion(engine);
        assertEquals(GamePhase.DAY_VOTE, engine.phase());

        int aliveBefore = state.alivePlayers().size();
        forceTimeout(engine);
        ActionResult result = engine.timeout();
        assertTrue(result.isSuccess());

        assertNull(state.getExiledSeat(), "没人投票就不该有人出局");
        assertEquals(aliveBefore, state.alivePlayers().size());
        // 没结束的话进入下一夜
        if (engine.phase() != GamePhase.FINISHED) {
            assertEquals(GamePhase.NIGHT, engine.phase(), "应该进入下一夜");
            assertEquals(2, state.getRound());
        }
    }

    @Test
    @DisplayName("投票超时但有部分人投了 → 按现有票数结算")
    void voteTimeoutSettlesWithPartialVotes() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();

        playNight(engine, victim.getSeatNo());
        finishDiscussion(engine);

        // 只让两个人投票，都投给同一个狼
        List<WerewolfPlayer> alive = state.alivePlayers();
        int target = state.aliveWolves().get(0).getSeatNo();
        int voted = 0;
        for (WerewolfPlayer player : alive) {
            if (player.getSeatNo() == target || voted >= 2) {
                continue;
            }
            engine.apply(ActionRequest.of(player.getSeatNo(), ActionType.VOTE, target));
            voted++;
        }

        forceTimeout(engine);
        assertTrue(engine.timeout().isSuccess());
        assertFalse(state.isAlive(target), "得票最多的人应该出局");
    }

    @Test
    @DisplayName("★ 全员挂机也不会无限循环：到最大轮数判平局收场")
    void allIdleEndsInDrawAtMaxRounds() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();

        // 一直超时、谁也不操作：夜晚空刀、白天无人出局
        int guard = 0;
        while (!engine.isFinished() && guard++ < 400) {
            forceTimeout(engine);
            engine.timeout();
        }

        assertTrue(engine.isFinished(), "应该被兜底结束，不能无限循环");
        assertEquals(WerewolfEngine.WINNER_DRAW, engine.winnerCamp());
        assertEquals(GamePhase.FINISHED, engine.phase());
        assertTrue(state.getRound() >= WerewolfEngine.MAX_ROUNDS - 1,
                "轮数应该到上限了，实际 " + state.getRound());
    }

    @Test
    @DisplayName("游戏结束后再调 timeout 会被拒绝")
    void timeoutRejectedAfterGameFinished() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        List<WerewolfPlayer> villagers = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).toList();

        // 走完一夜 + 一次投票，让狼人追平好人从而结束
        playNight(engine, villagers.get(0).getSeatNo());
        finishDiscussion(engine);

        int target = villagers.get(1).getSeatNo();
        for (WerewolfPlayer player : new ArrayList<>(state.alivePlayers())) {
            int voteTarget = player.getSeatNo() == target
                    ? state.alivePlayers().stream().map(WerewolfPlayer::getSeatNo)
                        .filter(s -> s != player.getSeatNo()).findFirst().orElse(1)
                    : target;
            engine.apply(ActionRequest.of(player.getSeatNo(), ActionType.VOTE, voteTarget));
        }

        assertEquals(GamePhase.FINISHED, engine.phase());
        assertEquals(Camp.WOLF.name(), engine.winnerCamp());

        forceTimeout(engine);
        ActionResult result = engine.timeout();
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("已经结束"), result.getMessage());
    }

    // ============================================================
    // 掉线弃权（forfeit）：断线的人立刻跳过，不拖住全桌
    // ============================================================

    /** 把一个夜晚走完，顺序和引擎一致：狼人 → 女巫 → 预言家 */
    private void playNight(WerewolfEngine engine, int wolfTargetSeat) {
        WerewolfState state = engine.getState();
        allWolvesKill(engine, wolfTargetSeat);

        WerewolfPlayer witch = state.findAliveByRole(Role.WITCH);
        if (witch != null && !state.isWitchActed()) {
            engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null));
        }

        WerewolfPlayer seer = state.findAliveByRole(Role.SEER);
        if (seer != null && !state.isSeerActed()) {
            int checkTarget = state.getPlayers().stream()
                    .filter(p -> p.isAlive() && p.getSeatNo() != seer.getSeatNo())
                    .map(WerewolfPlayer::getSeatNo).findFirst().orElseThrow();
            engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK, checkTarget));
        }
    }

    @Test
    @DisplayName("★ 一只狼掉线：按已经投出来的票定刀口，不用等超时")
    void offlineWolfForfeits() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        List<WerewolfPlayer> wolves = state.aliveWolves();
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();

        // 一只狼投了目标，另一只掉线
        engine.apply(ActionRequest.of(wolves.get(0).getSeatNo(), ActionType.WEREWOLF_KILL, victim.getSeatNo()));
        wolves.get(1).setOnline(false);

        ActionResult result = engine.forfeit(wolves.get(1).getSeatNo());
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(Integer.valueOf(victim.getSeatNo()), state.getWolfTarget(), "刀口应该按已投的票定下来");
    }

    @Test
    @DisplayName("★ 预言家和女巫掉线：本步跳过，夜晚照常结算")
    void offlineSeerAndWitchForfeit() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer seer = state.findAliveByRole(Role.SEER);
        WerewolfPlayer witch = state.findAliveByRole(Role.WITCH);
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();

        allWolvesKill(engine, victim.getSeatNo());
        assertEquals(NightStep.WITCH, state.getNightStep(), "狼人都决定了，轮到女巫");

        // 女巫先掉线
        assertTrue(engine.forfeit(witch.getSeatNo()).isSuccess(), "女巫掉线应该被跳过");
        assertTrue(state.isWitchActed());
        assertEquals(NightStep.SEER, state.getNightStep(), "轮到预言家了");
        assertEquals(GamePhase.NIGHT, engine.phase(), "预言家还没表态，仍然在夜晚");

        // 再轮到预言家掉线
        assertTrue(engine.forfeit(seer.getSeatNo()).isSuccess(), "预言家掉线应该被跳过");
        assertTrue(state.isSeerActed());
        assertEquals(GamePhase.DAY_DISCUSSION, engine.phase(), "都跳过之后应该进入白天");
        assertFalse(victim.isAlive(), "被刀的人该死（女巫掉线没救）");
    }

    @Test
    @DisplayName("★ 发言者掉线：立刻轮到下一个人")
    void offlineSpeakerForfeits() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();
        playNight(engine, victim.getSeatNo());
        assertEquals(GamePhase.DAY_DISCUSSION, engine.phase());

        int firstSpeaker = state.getSpeakOrder().get(0);
        int indexBefore = state.getSpeakIndex();
        state.findBySeat(firstSpeaker).setOnline(false);

        ActionResult result = engine.forfeit(firstSpeaker);
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(indexBefore + 1, state.getSpeakIndex(), "应该立刻跳到下一个发言者");
        assertTrue(state.getSpeeches().stream().anyMatch(s -> "（掉线跳过）".equals(s.get("text"))),
                "应该留下一条掉线记录");
    }

    @Test
    @DisplayName("★ 投票阶段全员掉线：都算弃权并正常结算，不会卡住")
    void offlineVotersForfeitAndSettle() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();
        playNight(engine, victim.getSeatNo());
        finishDiscussion(engine);
        assertEquals(GamePhase.DAY_VOTE, engine.phase());

        List<WerewolfPlayer> alive = new ArrayList<>(state.alivePlayers());
        for (int i = 0; i < alive.size(); i++) {
            engine.forfeit(alive.get(i).getSeatNo());
            if (i < alive.size() - 1) {
                assertEquals(i + 1, state.getAbstainedSeats().size(), "每掉线一个就记一个弃权");
            }
        }

        assertNull(state.getExiledSeat(), "全都弃权就没人被放逐");
        assertTrue(engine.phase() == GamePhase.NIGHT || engine.phase() == GamePhase.FINISHED,
                "应该继续往下走，实际停在 " + engine.phase());
    }

    @Test
    @DisplayName("已出局的人掉线不影响对局")
    void forfeitOnDeadPlayerIsRejected() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();
        victim.setAlive(false);

        ActionResult result = engine.forfeit(victim.getSeatNo());
        assertFalse(result.isSuccess());
    }
}
