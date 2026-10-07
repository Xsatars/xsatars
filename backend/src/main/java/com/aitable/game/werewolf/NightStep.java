package com.aitable.game.werewolf;

/**
 * 夜晚的行动顺序：<b>狼人 → 女巫 → 预言家</b>。
 *
 * <p>为什么分三步而不是同时进行：女巫需要先知道"今晚谁被刀"才能决定救不救，
 * 所以必须排在狼人之后；预言家放最后，避免信息互相影响。</p>
 *
 * <p>注意：这个字段<b>只出现在引擎内部和每个人的私有提示里</b>，
 * 绝不能进公共状态——否则等于告诉大家"现在轮到女巫了"。</p>
 */
public enum NightStep {

    /** 狼人选择今晚刀谁 */
    WOLF("狼人行动"),

    /** 女巫决定救谁 / 毒谁 / 不用药 */
    WITCH("女巫行动"),

    /** 预言家查验一个人 */
    SEER("预言家行动");

    private final String label;

    NightStep(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
