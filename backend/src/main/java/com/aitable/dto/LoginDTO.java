package com.aitable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求参数。
 *
 * <p>V0.1 的规则：账号不存在就直接注册（所以只有登录接口，没有单独的注册接口）。</p>
 */
public class LoginDTO {

    /** 登录名，也是注册时的账号 */
    @NotBlank(message = "不能为空")
    @Size(max = 50, message = "长度不能超过50")
    private String username;

    @NotBlank(message = "不能为空")
    @Size(min = 1, max = 32, message = "长度需在1-32之间")
    private String password;

    /** 昵称，可选；新注册时如果不填就用用户名 */
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
