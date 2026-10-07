package com.aitable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改昵称的请求参数。
 */
public class UpdateNicknameDTO {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 20, message = "昵称最多 20 个字")
    private String nickname;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
