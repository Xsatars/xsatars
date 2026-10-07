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

    /** 平民（2 个）：夜晚无行动 */
    VILLAGER("平民", Camp.GOOD);

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
