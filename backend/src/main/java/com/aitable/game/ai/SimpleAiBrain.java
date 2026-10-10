package com.aitable.game.ai;

import com.aitable.game.core.ActionRequest;
import com.aitable.game.core.ActionType;
import com.aitable.game.core.GameView;
import com.aitable.game.core.PlayerPrivateState;
import com.aitable.game.core.PublicGameState;
import com.aitable.game.core.SeatView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 规则 AI：不接大模型，纯按规则出牌。
 *
 * <p>它有两个价值：</p>
 * <ol>
 *   <li><b>一个人也能开局</b>——房主点几下加 5 个 AI，自己就能打一局</li>
 *   <li><b>把整条链路验通</b>——AI 决策 → 请求引擎 → 引擎校验 → 广播，
 *       之后再把这套脑子换成真的大模型时，外面的东西一行都不用改</li>
 * </ol>
 *
 * <p>策略上都留了一手"像人"的地方：女巫第一晚不毒人（信息太少）、
 * 预言家优先查没查过的、好人投票时会优先投预言家报出来的狼。</p>
 */
public class SimpleAiBrain implements AiBrain {

    @Override
    public String name() {
        return "规则AI";
    }

    @Override
    public ActionRequest decide(GameView view) {
        if (view == null || view.getPublicState() == null || view.getPrivateState() == null) {
            return null;
        }
        PublicGameState pub = view.getPublicState();
        PlayerPrivateState priv = view.getPrivateState();
        List<String> actions = priv.getAvailableActions();
        if (actions == null || actions.isEmpty()) {
            return null;
        }

        int seat = priv.getMySeat();

        // ⓪ 猎人开枪（8 人局）：挑一个"最像狼"的人带走。
        //    规则 AI 没什么判断力，就沿用投票那套逻辑 —— 有查验结果就优先打狼。
        //    注意这必须在最前面：猎人是"已经出局"的状态，别的分支都轮不到他。
        if (actions.contains(ActionType.HUNTER_SHOOT.name())) {
            Integer target = pickVoteTarget(pub, priv);
            if (target != null) {
                return ActionRequest.of(seat, ActionType.HUNTER_SHOOT, target);
            }
            return ActionRequest.of(seat, ActionType.PASS, null);   // 实在没人可打就不开枪
        }

        // ① 狼人刀人：挑一个不是队友的活人
        if (actions.contains(ActionType.WEREWOLF_KILL.name())) {
            List<Integer> targets = aliveOthers(pub, seat, priv.getTeammateSeats());
            if (!targets.isEmpty()) {
                return ActionRequest.of(seat, ActionType.WEREWOLF_KILL, randomOf(targets));
            }
        }

        // ② 女巫救人：能救就救（第一晚被刀的往往是队友，救了不亏）
        if (actions.contains(ActionType.WITCH_SAVE.name())) {
            return ActionRequest.of(seat, ActionType.WITCH_SAVE, null);
        }

        // ③ 女巫毒药：第一晚不毒（信息太少），之后随便毒一个
        if (actions.contains(ActionType.WITCH_POISON.name())) {
            if (pub.getRound() > 1) {
                List<Integer> targets = aliveOthers(pub, seat, null);
                if (!targets.isEmpty()) {
                    return ActionRequest.of(seat, ActionType.WITCH_POISON, randomOf(targets));
                }
            }
            return ActionRequest.of(seat, ActionType.PASS, null);
        }

        // ④ 预言家查验：优先查还没查过的人
        if (actions.contains(ActionType.SEER_CHECK.name())) {
            List<Integer> targets = aliveOthers(pub, seat, null);
            Map<Integer, Boolean> checked = seerResults(priv.getInfo());
            List<Integer> unchecked = new ArrayList<>();
            for (Integer t : targets) {
                if (!checked.containsKey(t)) {
                    unchecked.add(t);
                }
            }
            List<Integer> pool = unchecked.isEmpty() ? targets : unchecked;
            if (!pool.isEmpty()) {
                return ActionRequest.of(seat, ActionType.SEER_CHECK, randomOf(pool));
            }
        }

        // ⑤ 发言：说一句像人话的
        if (actions.contains(ActionType.SPEAK.name())) {
            return ActionRequest.speak(seat, pickSpeech());
        }

        // ⑥ 投票：有查验结果就投狼，否则随便投一个
        if (actions.contains(ActionType.VOTE.name())) {
            Integer target = pickVoteTarget(pub, priv);
            if (target != null) {
                return ActionRequest.of(seat, ActionType.VOTE, target);
            }
            return ActionRequest.of(seat, ActionType.PASS, null);
        }

        // ⑦ 兜底：还有 PASS 就直接过（比如女巫本晚不用药）
        if (actions.contains(ActionType.PASS.name())) {
            return ActionRequest.of(seat, ActionType.PASS, null);
        }
        return null;
    }

    // ============================================================
    // 小工具
    // ============================================================

    /** 还活着的其他人；exclude 里的人会被排除（狼人用来排除队友） */
    private List<Integer> aliveOthers(PublicGameState pub, int mySeat, List<Integer> exclude) {
        List<Integer> result = new ArrayList<>();
        for (SeatView seat : pub.getSeats()) {
            if (seat.getSeatNo() == mySeat || !seat.isAlive()) {
                continue;
            }
            if (exclude != null && exclude.contains(seat.getSeatNo())) {
                continue;
            }
            result.add(seat.getSeatNo());
        }
        return result;
    }

    private Integer randomOf(List<Integer> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    /** 预言家自己的查验记录：座位号 → 是不是狼 */
    @SuppressWarnings("unchecked")
    private Map<Integer, Boolean> seerResults(Map<String, Object> info) {
        if (info == null) {
            return Map.of();
        }
        Object raw = info.get("seerResults");
        if (!(raw instanceof Map)) {
            return Map.of();
        }
        Map<Integer, Boolean> result = new LinkedHashMap<>();
        ((Map<Object, Object>) raw).forEach((k, v) -> {
            try {
                result.put(Integer.valueOf(String.valueOf(k)), Boolean.TRUE.equals(v));
            } catch (NumberFormatException ignored) {
                // 脏数据就跳过
            }
        });
        return result;
    }

    /** 投票目标：优先投"预言家查出来的狼"，没有就随机 */
    private Integer pickVoteTarget(PublicGameState pub, PlayerPrivateState priv) {
        Map<Integer, Boolean> checked = seerResults(priv.getInfo());
        for (Map.Entry<Integer, Boolean> entry : checked.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue()) && isAlive(pub, entry.getKey())) {
                return entry.getKey();
            }
        }
        List<Integer> targets = aliveOthers(pub, priv.getMySeat(), null);
        return targets.isEmpty() ? null : randomOf(targets);
    }

    private boolean isAlive(PublicGameState pub, int seatNo) {
        for (SeatView seat : pub.getSeats()) {
            if (seat.getSeatNo() == seatNo) {
                return seat.isAlive();
            }
        }
        return false;
    }

    /**
     * 发言内容。
     *
     * <p>现在是几句写死的"场面话"；V0.2 第 4 步会把这里换成大模型生成的内容，
     * 那时候每个 AI 会有自己的性格和口吻。</p>
     */
    private String pickSpeech() {
        String[] lines = {
                "我先过一下，听听大家怎么说。",
                "我是好人，别投我，投错了亏一个轮次。",
                "昨晚的情况有点怪，我建议再观察观察。",
                "我没什么信息，先跟大流。",
                "大家各自说说自己的判断吧。",
                "我怀疑 3 号位，他刚才那句话太滑了。"
        };
        return lines[ThreadLocalRandom.current().nextInt(lines.length)];
    }
}
