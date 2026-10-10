package com.aitable.game.werewolf;

import com.aitable.common.enums.Role;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 板子 —— 一局用几个人、每个角色几个。
 *
 * <p>V0.4 之前这些数字是写死在 {@code GameConstants} 和发牌逻辑里的（只有 6 人局）。
 * 现在抽成枚举，加新板子只要在这里加一项，发牌、胜负判定、房间容量都会自动跟上。</p>
 *
 * <p><b>关于胜负判定</b>：两种板子都沿用同一套简单规则 ——
 * 「狼人数 ≥ 好人数 则狼胜」。标准线下规则里 8 人局常用"屠边"（杀光所有神职
 * 或所有平民），但那要给每个板子单独写一套判定，先不做；等真的有玩家反馈
 * 再拆开也不迟。</p>
 */
public enum Board {

    /** 标准 6 人局：2 狼 + 1 预言家 + 1 女巫 + 2 平民 */
    WEREWOLF_6("WEREWOLF_6", "标准 6 人局", "2 狼 · 预言家 · 女巫 · 2 平民",
            Map.of(
                    Role.WOLF, 2,
                    Role.SEER, 1,
                    Role.WITCH, 1,
                    Role.VILLAGER, 2)),

    /** 标准 8 人局：2 狼 + 1 预言家 + 1 女巫 + 1 猎人 + 3 平民 */
    WEREWOLF_8("WEREWOLF_8", "标准 8 人局", "2 狼 · 预言家 · 女巫 · 猎人 · 3 平民",
            Map.of(
                    Role.WOLF, 2,
                    Role.SEER, 1,
                    Role.WITCH, 1,
                    Role.HUNTER, 1,
                    Role.VILLAGER, 3));

    /** 默认板子（新建房间时用） */
    public static final Board DEFAULT = WEREWOLF_6;

    private final String code;
    private final String label;
    /** 给玩家看的角色构成说明 */
    private final String composition;
    private final Map<Role, Integer> roleCounts;

    Board(String code, String label, String composition, Map<Role, Integer> roleCounts) {
        this.code = code;
        this.label = label;
        this.composition = composition;
        this.roleCounts = roleCounts;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getComposition() {
        return composition;
    }

    public Map<Role, Integer> getRoleCounts() {
        return roleCounts;
    }

    /** 这个板子要几个人 */
    public int getPlayerCount() {
        return roleCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** 有几个狼（胜负判定和"狼队能看到队友"都要用） */
    public int getWolfCount() {
        return roleCounts.getOrDefault(Role.WOLF, 0);
    }

    /** 这个板子里有没有某个角色 */
    public boolean hasRole(Role role) {
        return roleCounts.getOrDefault(role, 0) > 0;
    }

    /**
     * 按这个板子洗出一副牌。
     *
     * @param random 传进来的随机源 —— 测试里给固定种子就能复现
     */
    public List<Role> buildDeck(Random random) {
        List<Role> deck = new ArrayList<>();
        roleCounts.forEach((role, count) -> {
            for (int i = 0; i < count; i++) {
                deck.add(role);
            }
        });
        Collections.shuffle(deck, random);
        return deck;
    }

    /** 按代号查板子，查不到就给默认的（容错，别让一个脏数据把开局搞崩） */
    public static Board byCode(String code) {
        if (code != null) {
            for (Board board : values()) {
                if (board.code.equalsIgnoreCase(code.trim())) {
                    return board;
                }
            }
        }
        return DEFAULT;
    }
}
