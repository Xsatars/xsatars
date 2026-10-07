package com.aitable.vo;

/**
 * 登录成功后返回给前端的数据。
 */
public class LoginVO {

    /** 后续所有请求都要带：Authorization: Bearer {token} */
    private String token;

    private UserVO user;

    /** 本次是不是新注册的账号（前端可以提示"已为你创建账号"） */
    private boolean newUser;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserVO getUser() {
        return user;
    }

    public void setUser(UserVO user) {
        this.user = user;
    }

    public boolean isNewUser() {
        return newUser;
    }

    public void setNewUser(boolean newUser) {
        this.newUser = newUser;
    }
}
