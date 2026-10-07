package com.aitable.game.werewolf;

import com.aitable.game.core.GamePhase;
import com.aitable.game.core.PublicGameState;
import com.aitable.game.core.SeatView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把"上帝视角"的 {@link WerewolfState} 转成能下发给玩家的公共状态。
 *
 * <p><b>这个类是信息隔离的第一道关口</b>：它输出的对象里
 * 绝对不会带上任何人的身份。谁是什么角色，只可能出现在
 * {@link WerewolfEngine#viewFor(int)} 里的私人状态部分。</p>
 */
public final class WerewolfViews {

    private WerewolfViews() {
    }

    /** 公共状态：房间里所有人的这一份是完全一样的 */
    public static PublicGameState publicState(WerewolfState state) {
        PublicGameState view = new PublicGameState();
        view.setGameId(state.getGameId());
        view.setRoomId(state.getRoomId());
        view.setPhase(state.getPhase().name());
        view.setPhaseLabel(state.getPhase().getLabel());
        view.setRound(state.getRound());
        view.setAliveCount(state.alivePlayers().size());
        view.setWinnerCamp(state.getWinnerCamp());
        view.setRecentMessages(new ArrayList<>(state.getMessages()));
        view.setDeadline(state.getDeadline());

        int speaker = currentSpeakerSeat(state);
        Map<Integer, Integer> voteCounts = voteCounts(state);
        boolean votePhase = state.getPhase() == GamePhase.DAY_VOTE;

        List<SeatView> seats = new ArrayList<>();
        for (WerewolfPlayer player : state.getPlayers()) {
            SeatView seat = new SeatView();
            seat.setSeatNo(player.getSeatNo());
            seat.setUserId(player.getUserId());
            seat.setNickname(player.getNickname());
            seat.setAlive(player.isAlive());
            seat.setSpeaking(player.getSeatNo() == speaker);
            seat.setVoteCount(voteCounts.getOrDefault(player.getSeatNo(), 0));
            // "已行动"只在投票阶段有意义（表示这个人投过票了）。
            // 夜晚绝不能暴露——否则能从"谁已经行动"推断出狼刀没刀、女巫用没用解药。
            seat.setActed(votePhase && (state.getDayVotes().containsKey(player.getSeatNo())
                    || state.getAbstainedSeats().contains(player.getSeatNo())));
            seat.setOnline(player.isOnline());
            seat.setAvatar(player.getAvatar());
            seats.add(seat);
            // ★ 注意：这里不设置任何角色相关字段 —— 公共状态里没有身份
        }
        view.setSeats(seats);
        return view;
    }

    /** 当前轮到哪个座位发言；不在发言阶段或已经发完则返回 -1 */
    public static int currentSpeakerSeat(WerewolfState state) {
        if (state.getPhase() != GamePhase.DAY_DISCUSSION) {
            return -1;
        }
        List<Integer> order = state.getSpeakOrder();
        int index = state.getSpeakIndex();
        if (index < 0 || index >= order.size()) {
            return -1;
        }
        return order.get(index);
    }

    /** 统计每个人当前得了几票（白天投票阶段用） */
    public static Map<Integer, Integer> voteCounts(WerewolfState state) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Integer target : state.getDayVotes().values()) {
            counts.merge(target, 1, Integer::sum);
        }
        return counts;
    }
}
