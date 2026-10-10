package com.aitable.dto;

import jakarta.validation.constraints.Size;

/**
 * 创建房间请求参数。
 */
public class CreateRoomDTO {

    /** 房间名，可选，不填就用"某人的狼人杀房间" */
    @Size(max = 100, message = "长度不能超过100")
    private String name;

    /**
     * 板子代号：{@code WEREWOLF_6}（6 人局）或 {@code WEREWOLF_8}（8 人局，带猎人）。
     *
     * <p>不填就用 6 人局。填了不认识的值也会退回 6 人局，不让一个脏参数把建房搞崩。</p>
     */
    @Size(max = 32, message = "长度不能超过32")
    private String boardCode;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBoardCode() {
        return boardCode;
    }

    public void setBoardCode(String boardCode) {
        this.boardCode = boardCode;
    }
}
