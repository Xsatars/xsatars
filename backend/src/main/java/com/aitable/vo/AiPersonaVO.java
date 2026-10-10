package com.aitable.vo;

import com.aitable.game.ai.AiPersona;

/**
 * 一个可选的 AI 性格（给前端的下拉框用）。
 *
 * <p>前端不硬编码性格列表，而是问后端要 —— 这样以后加性格只改后端一处，
 * 前端不用跟着发版。</p>
 */
public class AiPersonaVO {

    /** 提交时要回传的标识（如 HOTHEAD） */
    private String key;

    /** 显示的名字（如"阿豆"） */
    private String name;

    /** 一句话性格标签（如"急脾气"） */
    private String tag;

    /** 更完整的口吻描述，下拉里可以当提示文字 */
    private String style;

    public static AiPersonaVO from(AiPersona persona) {
        AiPersonaVO vo = new AiPersonaVO();
        vo.key = persona.getKey();
        vo.name = persona.getName();
        vo.tag = persona.getTag();
        vo.style = persona.getStyle();
        return vo;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }
}
