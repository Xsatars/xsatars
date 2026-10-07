package com.aitable.vo;

import com.aitable.entity.User;

/**
 * 用户信息（返回给前端）。
 *
 * <p>注意：**绝不包含 password 字段**。</p>
 */
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    /** USER=普通玩家 ADMIN=管理员，前端据此决定是否显示"管理后台"入口 */
    private String role;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
