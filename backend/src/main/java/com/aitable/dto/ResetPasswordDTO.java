package com.aitable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 忘记密码、重新设置密码的请求参数。
 *
 * <p>没登录，所以要靠「用户名 + 昵称」来证明这个号是你的。</p>
 */
public class ResetPasswordDTO {

    @NotBlank(message = "请输入用户名")
    private String username;

    @NotBlank(message = "请输入昵称")
    private String nickname;

    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 32, message = "新密码长度 6-32 位")
    private String newPassword;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
