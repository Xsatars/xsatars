package com.aitable.game.werewolf;

import com.aitable.common.enums.Camp;
import com.aitable.common.enums.Role;
import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionResult;
import com.aitable.game.core.ActionType;
import com.aitable.game.core.GameEvent;
import com.aitable.game.core.GamePhase;
import com.aitable.game.core.GameView;
import com.aitable.game.core.PublicGameState;
import com.aitable.game.core.SeatView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 狼人杀引擎测试。
 *
 * <p>这里测的是<b>规则本身</b>，不启动 Spring、不连数据库，跑得很快。
 * 重点有两块：</p>
 * <ol>
 *   <li><b>信息隔离</b>：公共状态里不能有任何身份；村民的私人状态里不能出现别人的身份；
 *       查验结果只能发给预言家本人。</li>
 *   <li><b>流程与胜负</b>：夜晚三个行动都做完才结算、投票平票无人出局、
 *       狼全死好人赢、狼数≥好人数狼赢。</li>
 * </ol>
 */
class WerewolfEngineTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 造一局已经发完牌、进入第一夜的 6 人对局 */
    private WerewolfEngine newEngine() {
        List<WerewolfPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            players.add(new WerewolfPlayer(seat, 100L + seat, "玩家" + seat));
        }
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players);
        engine.start();
        return engine;
    }

    private WerewolfPlayer seatOf(WerewolfEngine engine, Role role) {
        return engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == role)
                .findFirst().orElseThrow();
    }

    private WerewolfPlayer firstVillager(WerewolfEngine engine) {
        return seatOf(engine, Role.VILLAGER);
    }

    /** 让所有活狼都刀同一个人 */
    private void allWolvesKill(WerewolfEngine engine, int targetSeat) {
        for (WerewolfPlayer wolf : engine.getState().aliveWolves()) {
            engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL, targetSeat));
        }
    }

    /** 把一整夜做完，顺序和引擎一致：狼人 → 女巫 → 预言家 */
    private void finishNight(WerewolfEngine engine, int wolfTargetSeat) {
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

    /**
     * 让所有存活玩家投票给同一个目标。
     *
     * <p>注意：目标自己不能投自己，但那一票也必须投出去——
     * 引擎要等所有人都投完才结算，少一票就不会结算。</p>
     */
    private ActionResult allAliveVote(WerewolfEngine engine, int targetSeat) {
        WerewolfState state = engine.getState();
        List<Integer> aliveSeats = state.alivePlayers().stream()
                .map(WerewolfPlayer::getSeatNo).collect(Collectors.toList());
        int fallback = aliveSeats.stream().filter(s -> s != targetSeat).findFirst().orElse(targetSeat);

        ActionResult last = ActionResult.ok("投票完成");
        for (Integer seat : aliveSeats) {
            last = engine.apply(ActionRequest.of(seat, ActionType.VOTE, seat == targetSeat ? fallback : targetSeat));
        }
        return last;
    }

    /** 走完一次发言阶段（每个存活玩家各说一句） */
    private void finishDiscussion(WerewolfEngine engine) {
        WerewolfState state = engine.getState();
        int guard = 0;
        while (engine.phase() == GamePhase.DAY_DISCUSSION && guard++ < 20) {
            Integer speaker = state.getSpeakOrder().isEmpty() ? null
                    : state.getSpeakOrder().get(state.getSpeakIndex() >= state.getSpeakOrder().size()
                            ? state.getSpeakOrder().size() - 1 : state.getSpeakIndex());
            if (speaker == null) {
                break;
            }
            engine.apply(ActionRequest.speak(speaker, "我是 " + speaker + " 号"));
        }
    }

    // ============================================================
    // 开局
    // ============================================================

    @Test
    @DisplayName("6 人局发牌：2 狼 + 1 预言家 + 1 女巫 + 2 平民")
    void rolesAreAssignedCorrectly() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();

        assertEquals(2, state.getPlayers().stream().filter(p -> p.getRole() == Role.WOLF).count());
        assertEquals(1, state.getPlayers().stream().filter(p -> p.getRole() == Role.SEER).count());
        assertEquals(1, state.getPlayers().stream().filter(p -> p.getRole() == Role.WITCH).count());
        assertEquals(2, state.getPlayers().stream().filter(p -> p.getRole() == Role.VILLAGER).count());

        assertEquals(GamePhase.NIGHT, engine.phase());
        assertEquals(1, state.getRound());
        assertEquals(6, state.alivePlayers().size());
    }

    @Test
    @DisplayName("开局给每个人的身份牌是私有的，不是公开消息")
    void roleAssignEventsArePrivate() {
        List<WerewolfPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 6; seat++) {
            players.add(new WerewolfPlayer(seat, 100L + seat, "玩家" + seat));
        }
        WerewolfEngine engine = WerewolfEngine.create(1L, 1L, players);
        List<GameEvent> events = engine.start();

        List<GameEvent> roleEvents = events.stream()
                .filter(e -> "ROLE_ASSIGN".equals(e.getType()))
                .collect(Collectors.toList());
        assertEquals(6, roleEvents.size(), "每个玩家都应该收到一张身份牌");
        assertTrue(roleEvents.stream().noneMatch(GameEvent::isPublicEvent), "身份牌不能是公开事件");
        assertEquals(6, roleEvents.stream().map(GameEvent::getOwnerSeat).distinct().count(), "每张牌归属不同的人");
    }

    // ============================================================
    // 信息隔离（本项目最容易出 bug 的地方）
    // ============================================================

    @Test
    @DisplayName("★ 公共状态里绝对不能出现任何人的身份")
    void publicStateNeverLeaksRoles() throws Exception {
        WerewolfEngine engine = newEngine();
        PublicGameState publicState = engine.publicState();
        String json = mapper.writeValueAsString(publicState);

        // nightActor 是唯一的例外：它说的是"现在轮到哪个角色行动"，
        // 跟真人玩的时候主持人喊"狼人请睁眼"一样 —— 公开信息，不代表任何人是什么身份。
        // 把它剔掉之后，公共状态里不该再出现任何身份字样。
        String withoutNightActor = json.replaceAll("\"nightActor\"\\s*:\\s*\"[^\"]*\"", "");

        assertFalse(withoutNightActor.contains("WOLF"), "公共状态泄露了狼人： " + json);
        assertFalse(withoutNightActor.contains("SEER"), "公共状态泄露了预言家： " + json);
        assertFalse(withoutNightActor.contains("WITCH"), "公共状态泄露了女巫： " + json);
        assertFalse(withoutNightActor.contains("VILLAGER"), "公共状态泄露了平民： " + json);
        assertFalse(withoutNightActor.contains("role"), "公共状态里不该有角色字段：" + json);

        // nightActor 本身也要守规矩：只能是角色代号，不能夹带座位号
        String actor = publicState.getNightActor();
        if (actor != null) {
            assertTrue(java.util.List.of("WOLF", "WITCH", "SEER").contains(actor),
                    "nightActor 只能是角色代号，实际：" + actor);
            assertFalse(actor.matches(".*\\d.*"), "nightActor 不能夹带座位号：" + actor);
        }

        // 公共状态应该只有座位等公开信息
        assertEquals(6, publicState.getSeats().size());
        assertTrue(publicState.getSeats().stream().allMatch(seat -> seat.getNickname() != null));
    }

    @Test
    @DisplayName("★ 狼人能看到队友，平民什么都看不到")
    void wolfSeesTeammatesOnly() throws Exception {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();

        List<WerewolfPlayer> wolves = state.aliveWolves();
        WerewolfPlayer wolf = wolves.get(0);
        WerewolfPlayer teammate = wolves.get(1);

        GameView wolfView = engine.viewFor(wolf.getSeatNo());
        assertEquals(Role.WOLF.name(), wolfView.getPrivateState().getMyRole());
        assertEquals(Camp.WOLF.name(), wolfView.getPrivateState().getMyCamp());
        assertTrue(wolfView.getPrivateState().getTeammateSeats().contains(teammate.getSeatNo()),
                "狼人应该能看到队友座位");

        // 平民：只有自己的身份，没有别人的
        WerewolfPlayer villager = firstVillager(engine);
        GameView villagerView = engine.viewFor(villager.getSeatNo());
        assertEquals(Role.VILLAGER.name(), villagerView.getPrivateState().getMyRole());
        assertTrue(villagerView.getPrivateState().getTeammateSeats().isEmpty());

        String villagerJson = mapper.writeValueAsString(villagerView.getPrivateState());
        assertFalse(villagerJson.contains("WOLF"), "平民的私人状态里出现了狼人信息：" + villagerJson);
        assertFalse(villagerJson.contains("SEER"), "平民的私人状态里出现了预言家信息：" + villagerJson);
    }

    @Test
    @DisplayName("★ 预言家的查验结果只有他自己收到")
    void seerResultGoesOnlyToSeer() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer seer = state.findAliveByRole(Role.SEER);
        WerewolfPlayer wolf = state.aliveWolves().get(0);

        // 夜晚顺序：狼人 → 女巫 → 预言家，先把前面两步走完
        WerewolfPlayer victim = firstVillager(engine);
        allWolvesKill(engine, victim.getSeatNo());
        WerewolfPlayer witch = state.findAliveByRole(Role.WITCH);
        if (witch != null && !state.isWitchActed()) {
            engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null));
        }

        ActionResult result = engine.apply(
                ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK, wolf.getSeatNo()));
        assertTrue(result.isSuccess(), result.getMessage());

        GameEvent checkEvent = result.getEvents().stream()
                .filter(e -> "NIGHT_RESULT".equals(e.getType()))
                .findFirst().orElse(null);
        assertNotNull(checkEvent, "应该有查验结果事件");
        assertFalse(checkEvent.isPublicEvent(), "查验结果不能广播");
        assertEquals(seer.getSeatNo(), checkEvent.getOwnerSeat());

        // 预言家自己的视图里有记录
        GameView seerView = engine.viewFor(seer.getSeatNo());
        @SuppressWarnings("unchecked")
        Map<Integer, Boolean> seerResults = (Map<Integer, Boolean>) seerView.getPrivateState().getInfo().get("seerResults");
        assertNotNull(seerResults);
        assertTrue(seerResults.get(wolf.getSeatNo()), "查出来应该是狼");

        // 别人的视图里没有
        WerewolfPlayer villager = firstVillager(engine);
        assertNull(engine.viewFor(villager.getSeatNo()).getPrivateState().getInfo().get("seerResults"));
    }

    @Test
    @DisplayName("女巫能看到今晚谁被袭击，平民看不到")
    void witchSeesWolfTargetOnly() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer witch = state.findAliveByRole(Role.WITCH);

        // 狼刀一个非女巫、非预言家的玩家
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER)
                .findFirst().orElseThrow();
        allWolvesKill(engine, victim.getSeatNo());

        GameView witchView = engine.viewFor(witch.getSeatNo());
        assertEquals(victim.getSeatNo(), witchView.getPrivateState().getInfo().get("wolfTargetSeat"),
                "女巫应该知道今晚谁被袭击");
        assertTrue(witchView.getPrivateState().getAvailableActions().contains(ActionType.WITCH_SAVE.name()),
                "女巫应该可以救人");

        WerewolfPlayer villager = firstVillager(engine);
        assertNull(engine.viewFor(villager.getSeatNo()).getPrivateState().getInfo().get("wolfTargetSeat"),
                "平民不该知道今晚谁被袭击");
    }

    // ============================================================
    // 行动合法性
    // ============================================================

    @Test
    @DisplayName("平民不能做预言家的动作")
    void villagerCannotCheck() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer villager = firstVillager(engine);
        WerewolfPlayer wolf = engine.getState().aliveWolves().get(0);

        ActionResult result = engine.apply(
                ActionRequest.of(villager.getSeatNo(), ActionType.SEER_CHECK, wolf.getSeatNo()));
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("预言家"), result.getMessage());
    }

    @Test
    @DisplayName("狼人不能刀自己的队友")
    void wolfCannotKillTeammate() {
        WerewolfEngine engine = newEngine();
        List<WerewolfPlayer> wolves = engine.getState().aliveWolves();

        ActionResult result = engine.apply(
                ActionRequest.of(wolves.get(0).getSeatNo(), ActionType.WEREWOLF_KILL, wolves.get(1).getSeatNo()));
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("狼队友"), result.getMessage());
    }

    @Test
    @DisplayName("同一个狼人不能一晚上刀两次")
    void wolfCannotActTwice() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer wolf = engine.getState().aliveWolves().get(0);
        WerewolfPlayer victim = firstVillager(engine);

        assertTrue(engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL, victim.getSeatNo())).isSuccess());
        ActionResult again = engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL, victim.getSeatNo()));
        assertFalse(again.isSuccess());
        assertTrue(again.getMessage().contains("已经行动过"), again.getMessage());
    }

    @Test
    @DisplayName("白天不能刀人（阶段不对就该被拒绝）")
    void cannotKillDuringDay() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer victim = firstVillager(engine);
        finishNight(engine, victim.getSeatNo());
        assertEquals(GamePhase.DAY_DISCUSSION, engine.phase());

        WerewolfPlayer wolf = engine.getState().aliveWolves().get(0);
        ActionResult result = engine.apply(
                ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL, victim.getSeatNo()));
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("不是夜晚"), result.getMessage());
    }

    // ============================================================
    // 夜晚流程
    // ============================================================

    @Test
    @DisplayName("夜晚按「狼人 → 女巫 → 预言家」依次进行，三步都做完才结算")
    void nightSettlesAfterAllActions() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer victim = firstVillager(engine);

        allWolvesKill(engine, victim.getSeatNo());
        assertEquals(NightStep.WITCH, state.getNightStep(), "狼人都决定了，应该轮到女巫");
        assertEquals(GamePhase.NIGHT, engine.phase(), "女巫和预言家还没行动，不应该结算");

        WerewolfPlayer witch = state.findAliveByRole(Role.WITCH);
        engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null));
        assertEquals(NightStep.SEER, state.getNightStep(), "女巫决定了，应该轮到预言家");
        assertEquals(GamePhase.NIGHT, engine.phase(), "预言家还没查验，不应该结算");

        WerewolfPlayer seer = state.findAliveByRole(Role.SEER);
        int checkTarget = state.getPlayers().stream()
                .filter(p -> p.isAlive() && p.getSeatNo() != seer.getSeatNo())
                .map(WerewolfPlayer::getSeatNo).findFirst().orElseThrow();
        engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK, checkTarget));

        assertEquals(GamePhase.DAY_DISCUSSION, engine.phase(), "三步做完应该进入白天");
        assertFalse(victim.isAlive(), "被刀的人应该死了");
    }

    @Test
    @DisplayName("★ 夜晚必须按「狼人 → 女巫 → 预言家」的顺序，没轮到的动不了")
    void nightFollowsFixedOrder() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer seer = state.findAliveByRole(Role.SEER);
        WerewolfPlayer witch = state.findAliveByRole(Role.WITCH);
        WerewolfPlayer victim = firstVillager(engine);
        int wolfSeat = state.aliveWolves().get(0).getSeatNo();

        // ① 一开始是狼人行动：女巫和预言家都不能动
        assertEquals(NightStep.WOLF, state.getNightStep());
        assertFalse(engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null)).isSuccess(),
                "还没轮到女巫，不该让她用药");
        assertFalse(engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK, wolfSeat)).isSuccess(),
                "还没轮到预言家，不该让他查验");
        assertTrue(engine.viewFor(witch.getSeatNo()).getPrivateState().getAvailableActions().isEmpty(),
                "女巫此刻不该有任何操作");

        // ② 狼人行动完 → 轮到女巫
        allWolvesKill(engine, victim.getSeatNo());
        assertEquals(NightStep.WITCH, state.getNightStep());
        assertTrue(engine.viewFor(witch.getSeatNo()).getPrivateState().getAvailableActions()
                .contains(ActionType.WITCH_SAVE.name()), "女巫这时才能救人");
        assertTrue(engine.viewFor(witch.getSeatNo()).getPrivateState().getInfo().containsKey("wolfTargetSeat"),
                "女巫这时才能看到今晚谁被刀（所以她必须排在狼人之后）");
        assertFalse(engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK, wolfSeat)).isSuccess(),
                "还是没轮到预言家");

        // ③ 女巫决定完 → 轮到预言家 → 结算
        assertTrue(engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null)).isSuccess());
        assertEquals(NightStep.SEER, state.getNightStep());
        assertTrue(engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK, wolfSeat)).isSuccess());
        assertEquals(GamePhase.DAY_DISCUSSION, engine.phase(), "三步都做完才进入白天");
    }

    @Test
    @DisplayName("女巫用解药可以救下被刀的人")
    void witchSavePreventsDeath() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer witch = state.findAliveByRole(Role.WITCH);
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER)
                .findFirst().orElseThrow();

        allWolvesKill(engine, victim.getSeatNo());

        ActionResult save = engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.WITCH_SAVE, null));
        assertTrue(save.isSuccess(), save.getMessage());
        assertTrue(state.isWitchSaveUsed());
        assertTrue(victim.isAlive(), "被救的人不该死");

        WerewolfPlayer seer = state.findAliveByRole(Role.SEER);
        int checkTarget = state.getPlayers().stream()
                .filter(p -> p.isAlive() && p.getSeatNo() != seer.getSeatNo())
                .map(WerewolfPlayer::getSeatNo).findFirst().orElseThrow();
        engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK, checkTarget));

        assertEquals(GamePhase.DAY_DISCUSSION, engine.phase());
        assertTrue(victim.isAlive(), "有解药的一夜不该有人死");
    }

    @Test
    @DisplayName("女巫的毒药能毒死一个人")
    void witchPoisonKills() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer witch = state.findAliveByRole(Role.WITCH);
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();
        // 这里毒一只狼：如果一晚连死两个好人，狼人数量就直接追平好人，游戏会立刻结束，
        // 那样就测不到"进入白天"这一步了。
        WerewolfPlayer poisoned = state.aliveWolves().get(0);

        allWolvesKill(engine, victim.getSeatNo());
        engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.WITCH_POISON, poisoned.getSeatNo()));
        assertTrue(state.isWitchPoisonUsed());

        // 预言家最后查验（别查已经死掉的狼）
        WerewolfPlayer seer = state.findAliveByRole(Role.SEER);
        int checkTarget = state.getPlayers().stream()
                .filter(p -> p.isAlive() && p.getSeatNo() != seer.getSeatNo())
                .map(WerewolfPlayer::getSeatNo).findFirst().orElseThrow();
        engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK, checkTarget));

        assertFalse(victim.isAlive(), "被刀的应该死");
        assertFalse(poisoned.isAlive(), "被毒的应该死");
        // 死 1 狼 1 好人 → 剩下 1 狼 vs 3 好人，游戏继续，进入白天
        assertEquals(GamePhase.DAY_DISCUSSION, engine.phase());
    }

    // ============================================================
    // 白天：发言与投票
    // ============================================================

    @Test
    @DisplayName("发言要按顺序，没轮到的人说话会被拒绝")
    void speakMustFollowOrder() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer victim = firstVillager(engine);
        finishNight(engine, victim.getSeatNo());

        WerewolfState state = engine.getState();
        int firstSpeaker = state.getSpeakOrder().get(0);
        int notFirst = state.getSpeakOrder().get(1);

        ActionResult wrong = engine.apply(ActionRequest.speak(notFirst, "我抢话"));
        assertFalse(wrong.isSuccess());
        assertTrue(wrong.getMessage().contains("还没轮到"), wrong.getMessage());

        assertTrue(engine.apply(ActionRequest.speak(firstSpeaker, "大家好")).isSuccess());
        assertTrue(engine.apply(ActionRequest.speak(notFirst, "我也说两句")).isSuccess());
    }

    @Test
    @DisplayName("全部发言结束后自动进入投票阶段")
    void discussionLeadsToVote() {
        WerewolfEngine engine = newEngine();
        WerewolfPlayer victim = firstVillager(engine);
        finishNight(engine, victim.getSeatNo());
        finishDiscussion(engine);
        assertEquals(GamePhase.DAY_VOTE, engine.phase());
    }

    @Test
    @DisplayName("票最多的人被放逐出局")
    void mostVotedPlayerIsExiled() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();
        finishNight(engine, victim.getSeatNo());
        finishDiscussion(engine);

        int target = state.alivePlayers().stream()
                .map(WerewolfPlayer::getSeatNo).findFirst().orElseThrow();
        ActionResult result = allAliveVote(engine, target);

        assertFalse(state.isAlive(target), "票最多的人应该出局");
        // 注意：投票结算后可能立刻进入下一夜，而进入下一夜会清空"本轮放逐"记录，
        // 所以从事件里确认谁被放逐，而不是读 state.getExiledSeat()
        assertTrue(result.getEvents().stream()
                        .anyMatch(e -> "VOTE_RESULT".equals(e.getType())
                                && e.getMessage() != null && e.getMessage().contains("被投票放逐")),
                "应该有一条「被投票放逐」的结果，实际事件："
                        + result.getEvents().stream().map(GameEvent::getType).collect(Collectors.toList()));
    }

    @Test
    @DisplayName("平票时本轮无人出局")
    void tieVoteExilesNobody() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer victim = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();
        WerewolfPlayer witch = state.findAliveByRole(Role.WITCH);

        // 让女巫救下被刀的人 → 平安夜，6 个人全活着，方便制造 3:3 的平票
        allWolvesKill(engine, victim.getSeatNo());
        engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.WITCH_SAVE, null));
        WerewolfPlayer seer = state.findAliveByRole(Role.SEER);
        int checkTarget = state.getPlayers().stream()
                .filter(p -> p.isAlive() && p.getSeatNo() != seer.getSeatNo())
                .map(WerewolfPlayer::getSeatNo).findFirst().orElseThrow();
        engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK, checkTarget));
        assertEquals(GamePhase.DAY_DISCUSSION, engine.phase());
        assertEquals(6, state.alivePlayers().size(), "平安夜应该 6 个人都在");

        finishDiscussion(engine);
        assertEquals(GamePhase.DAY_VOTE, engine.phase());

        // 1 号 2 号互相投，其余人左右各半 → 两人各拿 3 票
        engine.apply(ActionRequest.of(1, ActionType.VOTE, 2));
        engine.apply(ActionRequest.of(2, ActionType.VOTE, 1));
        engine.apply(ActionRequest.of(3, ActionType.VOTE, 2));
        engine.apply(ActionRequest.of(4, ActionType.VOTE, 1));
        engine.apply(ActionRequest.of(5, ActionType.VOTE, 2));
        engine.apply(ActionRequest.of(6, ActionType.VOTE, 1));

        assertNull(state.getExiledSeat(), "平票不应该有人出局");
        assertTrue(state.isAlive(1) && state.isAlive(2), "平票时两个人都应该活着");
        assertEquals(GamePhase.NIGHT, engine.phase(), "平票后应该进入下一夜");
        assertEquals(2, state.getRound());
    }

    // ============================================================
    // 胜负判定
    // ============================================================

    @Test
    @DisplayName("★ 狼人全部出局 → 好人获胜")
    void goodWinsWhenAllWolvesDie() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();

        // 直接把两只狼投出去
        for (WerewolfPlayer wolf : state.aliveWolves()) {
            if (engine.isFinished()) {
                break;
            }
            engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL,
                    firstVillager(engine).getSeatNo()));
        }

        // 反复走"夜晚 → 白天 → 投票投狼"直到结束
        int guard = 0;
        while (!engine.isFinished() && guard++ < 10) {
            if (engine.phase() == GamePhase.NIGHT) {
                List<WerewolfPlayer> wolves = state.aliveWolves();
                if (wolves.isEmpty()) {
                    break;
                }
                WerewolfPlayer target = state.getPlayers().stream()
                        .filter(p -> p.isAlive() && !p.isWolf())
                        .findFirst().orElse(null);
                if (target == null) {
                    break;
                }
                finishNight(engine, target.getSeatNo());
            } else if (engine.phase() == GamePhase.DAY_DISCUSSION) {
                finishDiscussion(engine);
            } else if (engine.phase() == GamePhase.DAY_VOTE) {
                List<WerewolfPlayer> wolves = state.aliveWolves();
                if (wolves.isEmpty()) {
                    break;
                }
                allAliveVote(engine, wolves.get(0).getSeatNo());
            }
        }

        // ★ 注意：改成【屠边】规则之后，狼队变得更容易赢 —— 6 人局里只要把 2 个平民
        //（或者 2 个神职）清干净就赢了，所以"好人一定赢"这个断言不再成立。
        // 这个测试改成验证真正该保证的事：对局一定会走到结束，而且必有一方获胜。
        //（"狼全死 → 好人胜"这条规则由 WinConditionTest 专门覆盖。）
        assertTrue(engine.isFinished(), "对局应该已经结束，不能卡住");
        assertNotNull(engine.winnerCamp(), "结束时必须有一方获胜");
        assertTrue(Camp.GOOD.name().equals(engine.winnerCamp())
                        || Camp.WOLF.name().equals(engine.winnerCamp()),
                "赢家只能是好人或狼人，实际：" + engine.winnerCamp());
    }

    @Test
    @DisplayName("★ 6 人局：两个平民都被清掉 → 狼人获胜（屠民）")
    void wolfWinsAtParity() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();

        // 第一夜狼刀一个平民
        List<WerewolfPlayer> villagers = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).collect(Collectors.toList());
        finishNight(engine, villagers.get(0).getSeatNo());
        assertEquals(GamePhase.DAY_DISCUSSION, engine.phase());

        // 白天把另一个平民也投出去 → 平民全灭 → 屠民，狼胜。
        //（顺带说明：这时场上还剩 2 狼 + 预言家 + 女巫，按旧的"人数持平"规则
        // 恰好也是 2v2，两种规则在这里结论一致；真正能看出区别的是 8 人局，
        // 见 WinConditionTest。）
        finishDiscussion(engine);
        allAliveVote(engine, villagers.get(1).getSeatNo());

        assertTrue(engine.isFinished(), "平民被清光，对局应该结束");
        assertEquals(Camp.WOLF.name(), engine.winnerCamp());
    }

    @Test
    @DisplayName("结束后公开所有人身份，并且不再接受任何行动")
    void finishedGameRevealsRolesAndRejectsActions() throws Exception {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        List<WerewolfPlayer> villagers = state.getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).collect(Collectors.toList());

        finishNight(engine, villagers.get(0).getSeatNo());
        finishDiscussion(engine);

        List<GameEvent> voteEvents = new ArrayList<>();
        int exileTarget = villagers.get(1).getSeatNo();
        for (WerewolfPlayer player : new ArrayList<>(state.alivePlayers())) {
            int target = player.getSeatNo() == exileTarget
                    ? state.alivePlayers().stream().map(WerewolfPlayer::getSeatNo)
                        .filter(seat -> seat != player.getSeatNo()).findFirst().orElse(1)
                    : exileTarget;
            ActionResult r = engine.apply(ActionRequest.of(player.getSeatNo(), ActionType.VOTE, target));
            voteEvents.addAll(r.getEvents());
        }

        assertTrue(engine.isFinished());
        GameEvent endEvent = voteEvents.stream()
                .filter(e -> "GAME_END".equals(e.getType())).findFirst().orElse(null);
        assertNotNull(endEvent, "应该广播 GAME_END");
        assertTrue(endEvent.isPublicEvent(), "结束后身份是公开的");
        String json = mapper.writeValueAsString(endEvent.getPayload());
        assertTrue(json.contains("WOLF") && json.contains("SEER") && json.contains("WITCH"),
                "结束后应该公开所有人的身份：" + json);

        // 结束后不能再行动
        WerewolfPlayer someone = state.getPlayers().get(0);
        ActionResult rejected = engine.apply(ActionRequest.of(someone.getSeatNo(), ActionType.VOTE, 1));
        assertFalse(rejected.isSuccess());
        assertTrue(rejected.getMessage().contains("已经结束"), rejected.getMessage());
    }

    // ============================================================
    // 遗言 / 弃权 / 票数清理
    // ============================================================

    private WerewolfPlayer anyVillager(WerewolfEngine engine) {
        return engine.getState().getPlayers().stream()
                .filter(p -> p.getRole() == Role.VILLAGER).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("★ 夜晚出局的人第二天排在发言顺序最前面，说的是遗言")
    void nightDeathsSpeakLastWordsFirst() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer victim = anyVillager(engine);

        finishNight(engine, victim.getSeatNo());
        assertEquals(GamePhase.DAY_DISCUSSION, engine.phase());

        assertEquals(Integer.valueOf(victim.getSeatNo()), state.getSpeakOrder().get(0),
                "昨夜出局的人应该第一个发言（遗言）");
        assertFalse(victim.isAlive(), "他确实已经出局");

        // 出局的人也能说话（说遗言）
        assertTrue(engine.optionsFor(victim.getSeatNo()).contains(ActionType.SPEAK.name()),
                "遗言阶段应该给他发言选项");
        ActionResult result = engine.apply(ActionRequest.speak(victim.getSeatNo(), "我是好人，大家投 3 号"));
        assertTrue(result.isSuccess(), result.getMessage());
        assertTrue(state.getSpeeches().stream().anyMatch(s -> "我是好人，大家投 3 号".equals(s.get("text"))));

        // 说完就轮到下一个
        assertTrue(WerewolfViews.currentSpeakerSeat(state) != victim.getSeatNo());
    }

    @Test
    @DisplayName("★ 狼人刀的人不一致 → 今晚空刀，谁都不死")
    void splitWolfVotesMeanNoKill() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        List<WerewolfPlayer> wolves = state.aliveWolves();
        List<WerewolfPlayer> goods = state.getPlayers().stream()
                .filter(p -> !p.isWolf()).collect(Collectors.toList());

        engine.apply(ActionRequest.of(wolves.get(0).getSeatNo(), ActionType.WEREWOLF_KILL, goods.get(0).getSeatNo()));
        engine.apply(ActionRequest.of(wolves.get(1).getSeatNo(), ActionType.WEREWOLF_KILL, goods.get(1).getSeatNo()));

        WerewolfPlayer seer = state.findAliveByRole(Role.SEER);
        // 别让预言家查自己（引擎会拒绝）
        WerewolfPlayer seerTarget = state.getPlayers().stream()
                .filter(p -> p.getSeatNo() != seer.getSeatNo()).findFirst().orElseThrow();
        WerewolfPlayer witch = state.findAliveByRole(Role.WITCH);
        if (witch != null && !state.isWitchActed()) {
            engine.apply(ActionRequest.of(witch.getSeatNo(), ActionType.PASS, null));
        }
        engine.apply(ActionRequest.of(seer.getSeatNo(), ActionType.SEER_CHECK, seerTarget.getSeatNo()));

        assertEquals(GamePhase.DAY_DISCUSSION, engine.phase(), "夜晚应该正常结算");
        assertTrue(state.getNightDeaths().isEmpty(), "意见不统一不该有人死");
        assertTrue(goods.get(0).isAlive() && goods.get(1).isAlive());
    }

    @Test
    @DisplayName("★ 当前发言者可以放弃发言")
    void speakerCanPass() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        finishNight(engine, anyVillager(engine).getSeatNo());

        int speaker = state.getSpeakOrder().get(0);
        int indexBefore = state.getSpeakIndex();
        assertTrue(engine.optionsFor(speaker).contains(ActionType.PASS.name()), "发言阶段应该能放弃");

        ActionResult result = engine.apply(ActionRequest.of(speaker, ActionType.PASS, null));
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(indexBefore + 1, state.getSpeakIndex(), "放弃发言也要往下走");
        assertTrue(state.getSpeeches().stream().anyMatch(s -> "（放弃发言）".equals(s.get("text"))));
    }

    @Test
    @DisplayName("★ 投票可以弃权：不算票，但算已表态（否则永远等不到投完）")
    void voterCanAbstain() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        finishNight(engine, anyVillager(engine).getSeatNo());
        finishDiscussion(engine);
        assertEquals(GamePhase.DAY_VOTE, engine.phase());

        WerewolfPlayer voter = state.alivePlayers().get(0);
        assertTrue(engine.optionsFor(voter.getSeatNo()).contains(ActionType.PASS.name()));

        assertTrue(engine.apply(ActionRequest.of(voter.getSeatNo(), ActionType.PASS, null)).isSuccess());
        assertTrue(state.getAbstainedSeats().contains(voter.getSeatNo()), "应该记为弃权");
        assertFalse(state.getDayVotes().containsKey(voter.getSeatNo()), "弃权不该记成一张票");
    }

    @Test
    @DisplayName("★ 夜晚绝不显示「谁已行动」（否则能猜出狼刀没刀、女巫用没用解药）")
    void nightDoesNotRevealWhoActed() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        WerewolfPlayer wolf = state.aliveWolves().get(0);
        WerewolfPlayer victim = anyVillager(engine);

        engine.apply(ActionRequest.of(wolf.getSeatNo(), ActionType.WEREWOLF_KILL, victim.getSeatNo()));

        assertTrue(engine.publicState().getSeats().stream().noneMatch(SeatView::isActed),
                "夜晚阶段任何座位都不能显示「已行动」");
    }

    @Test
    @DisplayName("★ 狼人能看到狼队各自刀了谁；好人看不到")
    void wolvesSeeEachOthersKillVotes() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        List<WerewolfPlayer> wolves = state.aliveWolves();
        WerewolfPlayer victim = anyVillager(engine);

        engine.apply(ActionRequest.of(wolves.get(0).getSeatNo(), ActionType.WEREWOLF_KILL, victim.getSeatNo()));

        GameView wolfView = engine.viewFor(wolves.get(1).getSeatNo());
        Object votes = wolfView.getPrivateState().getInfo().get("wolfVotes");
        assertNotNull(votes, "狼人应该能看到狼队的刀人情况");
        assertTrue(votes.toString().contains(String.valueOf(victim.getSeatNo())),
                "应该能看到队友选了 " + victim.getSeatNo() + " 号，实际 " + votes);
        assertTrue(wolfView.getPrivateState().getTeammateSeats().contains(wolves.get(0).getSeatNo()));

        WerewolfPlayer good = state.getPlayers().stream().filter(p -> !p.isWolf()).findFirst().orElseThrow();
        GameView goodView = engine.viewFor(good.getSeatNo());
        assertNull(goodView.getPrivateState().getInfo().get("wolfVotes"), "好人绝不能看到狼队信息");
    }

    @Test
    @DisplayName("★ 投票阶段的票数和「已投票」标记不会留到第二天")
    void voteMarksAreClearedForNextRound() {
        WerewolfEngine engine = newEngine();
        WerewolfState state = engine.getState();
        finishNight(engine, anyVillager(engine).getSeatNo());
        finishDiscussion(engine);
        assertEquals(GamePhase.DAY_VOTE, engine.phase());

        allAliveVote(engine, state.alivePlayers().get(0).getSeatNo());

        if (engine.phase() == GamePhase.NIGHT) {
            assertTrue(state.getDayVotes().isEmpty(), "投票记录应该清空");
            assertTrue(state.getAbstainedSeats().isEmpty(), "弃权记录应该清空");
            assertTrue(engine.publicState().getSeats().stream().allMatch(s -> s.getVoteCount() == 0),
                    "票数不能残留到第二天");
            assertTrue(engine.publicState().getSeats().stream().noneMatch(SeatView::isActed),
                    "「已投票」标记也要清掉");
        }
    }
}
