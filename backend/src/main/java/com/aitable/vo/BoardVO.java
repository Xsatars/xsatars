package com.aitable.vo;

import com.aitable.common.enums.Role;
import com.aitable.game.werewolf.Board;

/**
 * 一个可选板子（给建房时的下拉用）。
 */
public class BoardVO {

    /** 板子代号，建房时回传这个 */
    private String code;

    /** 显示名，如"标准 8 人局" */
    private String label;

    /** 角色构成说明，如"2 狼 · 预言家 · 女巫 · 猎人 · 3 平民" */
    private String composition;

    /** 要几个人 */
    private int playerCount;

    /** 有几个狼（前端有时想标一下"难度"） */
    private int wolfCount;

    public static BoardVO from(Board board) {
        BoardVO vo = new BoardVO();
        vo.code = board.getCode();
        vo.label = board.getLabel();
        vo.composition = board.getComposition();
        vo.playerCount = board.getPlayerCount();
        vo.wolfCount = board.getWolfCount();
        return vo;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getComposition() {
        return composition;
    }

    public void setComposition(String composition) {
        this.composition = composition;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(int playerCount) {
        this.playerCount = playerCount;
    }

    public int getWolfCount() {
        return wolfCount;
    }

    public void setWolfCount(int wolfCount) {
        this.wolfCount = wolfCount;
    }
}
