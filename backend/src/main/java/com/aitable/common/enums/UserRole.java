package com.aitable.common.enums;

/**
 * 用户角色。
 *
 * <p>管理员能看到管理后台（数据概览、用户管理、房间管理、游戏记录）。
 * 谁是管理员由配置项 {@code ai-table.admin-usernames} 决定：
 * 用户名在名单里的用户，登录时会自动被标记为 ADMIN。</p>
 */
public enum UserRole {

    /** 普通玩家 */
    USER,

    /** 管理员 */
    ADMIN;

    public static boolean isAdmin(String role) {
        return ADMIN.name().equals(role);
    }
}
