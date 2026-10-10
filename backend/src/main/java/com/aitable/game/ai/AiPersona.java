package com.aitable.game.ai;

import java.util.Collection;

/**
 * AI 玩家的性格。
 *
 * <p>之前每个 AI 只有名字，脑子里是同一个模板 —— 玩两局就会发现"换了一批人，说话还是那个味"。
 * 这一版给它们各自的人设：<b>说话口吻</b>（提示词里怎么描述）、
 * <b>行为倾向</b>（多激进、多话痨，规则 AI 也用得上）。</p>
 *
 * <p>性格是<b>绑在席位上的</b>，一局开始就定了，中途不会变 ——
 * 这样玩家能形成印象："那个急性子又开始了"。</p>
 */
public enum AiPersona {

    /** 急脾气：上来就下结论，谁磨叽就催 */
    HOTHEAD("阿豆", "急脾气", 85, 55,
            "你性子急，喜欢快下结论，看谁磨叽就直接催他，不爱听人绕弯子。"),

    /** 阴阳怪气：话不多，但每句都带刺 */
    SNEAKY("小狼", "阴阳怪气", 45, 35,
            "你话不多，但说话带刺、爱暗示，喜欢说「行吧」「随你们」这种话，偶尔冷不丁点一句要害。"),

    /** 慢半拍：反应慢，经常搞错，但偶尔蒙对 */
    DOPEY("糊涂仙", "慢半拍", 30, 70,
            "你反应有点慢，说话啰嗦还常常记错人，但偶尔会突然蒙对关键信息，自己也不太确定。"),

    /** 直球：想到什么说什么，不懂套路 */
    BLUNT("铁憨憨", "直球", 70, 45,
            "你说话特别直，想到什么说什么，完全不会绕弯子，也不懂什么套路，别人说啥你信啥。"),

    /** 冷静分析：讲逻辑、列证据 */
    ANALYST("夜观星", "冷静分析", 35, 75,
            "你说话冷静克制，喜欢摆事实讲逻辑，会把前面几轮谁说过什么、谁投过谁拿出来对照分析。"),

    /** 话痨：热情、话多、爱跑题 */
    CHATTY("话匣子", "话痨", 55, 95,
            "你特别能说，一开口就停不下来，热情又爱跑题，经常一句话里塞三件事。");

    /** 数据库里存的标识 */
    private final String key;
    /** 显示用的名字 */
    private final String name;
    /** 一句话性格标签（界面上显示） */
    private final String tag;
    /** 多激进（0~100）：越高越早下结论、越敢带节奏 */
    private final int aggression;
    /** 多话痨（0~100）：影响发言长度和是否爱接话 */
    private final int chattiness;
    /** 给大模型的说话风格描述 */
    private final String style;

    AiPersona(String name, String tag, int aggression, int chattiness, String style) {
        this.key = name();
        this.name = name;
        this.tag = tag;
        this.aggression = aggression;
        this.chattiness = chattiness;
        this.style = style;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public int getAggression() {
        return aggression;
    }

    public int getChattiness() {
        return chattiness;
    }

    public String getStyle() {
        return style;
    }

    /** 给提示词用的一整段人设描述 */
    public String describe() {
        return "你叫「" + name + "」，人设是「" + tag + "」。" + style;
    }

    /** 按 key 找；找不到就返回 null（兼容老数据） */
    public static AiPersona byKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        for (AiPersona persona : values()) {
            if (persona.key.equals(key) || persona.name.equals(key)) {
                return persona;
            }
        }
        return null;
    }

    /**
     * 挑一个这桌还没用过的性格。
     *
     * <p>按固定顺序取，保证同一个房间反复加 AI 时人设不重复、也不会随机到让人困惑。</p>
     *
     * @param usedNames 房间里已经用掉的名字
     */
    public static AiPersona pick(Collection<String> usedNames) {
        for (AiPersona persona : values()) {
            if (!usedNames.contains(persona.name)) {
                return persona;
            }
        }
        return DOPEY;
    }
}
