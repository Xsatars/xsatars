package com.aitable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求参数。
 *
 * <p>「确认密码」只在前端比对，不需要传给后端。</p>
 */
public class RegisterDTO {

    @NotBlank(message = "不能为空")
    @Size(min = 3, max = 50, message = "长度需在3-50个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "不能为空")
    @Size(min = 6, max = 32, message = "长度需在6-32个字符之间")
    private String password;

    /** 昵称：游戏里显示的名字 */
    @NotBlank(message = "不能为空")
    @Size(max = 50, message = "长度不能超过50")
    private String nickname;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
