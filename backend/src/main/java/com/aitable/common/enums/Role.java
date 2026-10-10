package com.aitable.common.enums;

/**
 * 角色。V0.1 的 6 人局只用这四种。
 *
 * <p>每个角色属于哪个阵营见 {@link #camp()}。</p>
 */
public enum Role {

    /** 狼人（2 个）：每晚共同刀一人 */
    WOLF("狼人", Camp.WOLF),

    /** 预言家（1 个）：每晚查验一人身份 */
    SEER("预言家", Camp.GOOD),

    /** 女巫（1 个）：一瓶解药、一瓶毒药 */
    WITCH("女巫", Camp.GOOD),

    /** 平民（6 人局 2 个 / 8 人局 3 个）：夜晚无行动 */
    VILLAGER("平民", Camp.GOOD),

    /**
     * 猎人（8 人局，1 个）。
     *
     * <p>被狼刀死、或被投票放逐时，可以<b>开枪带走一个人</b>。</p>
     *
     * <p>但<b>被女巫毒死不能开枪</b> —— 这是标准规则，也是这个角色的平衡点：
     * 狼队可以用毒药"废掉"猎人的枪（前提是他们猜得到谁是猎人）。</p>
     */
    HUNTER("猎人", Camp.GOOD);

    private final String label;
    private final Camp camp;

    Role(String label, Camp camp) {
        this.label = label;
        this.camp = camp;
    }

    /** 中文名，用于前端展示 */
    public String getLabel() {
        return label;
    }

    /** 所属阵营 */
    public Camp getCamp() {
        return camp;
    }

    /** 是否是狼人 */
    public boolean isWolf() {
        return this == WOLF;
    }
}
