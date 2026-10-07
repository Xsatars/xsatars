package com.aitable.dto;

import jakarta.validation.constraints.Size;

/**
 * 创建房间请求参数。
 */
public class CreateRoomDTO {

    /** 房间名，可选，不填就用"某人的狼人杀房间" */
    @Size(max = 100, message = "长度不能超过100")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
