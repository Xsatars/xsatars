package com.aitable.service;

import com.aitable.common.PageResult;
import com.aitable.vo.AdminGameDetailVO;
import com.aitable.vo.AdminGameVO;
import com.aitable.vo.AdminOverviewVO;
import com.aitable.vo.AdminRoomVO;
import com.aitable.vo.AdminUserVO;

/**
 * 管理后台业务。
 *
 * <p>V0.1 只有四块：数据概览、用户管理、房间管理、游戏记录。
 * AI 相关的管理（AI 玩家、决策记录、Prompt、运行状态）等 V0.3 之后再加。</p>
 */
public interface AdminService {

    /** 数据概览：各类数量统计 + MySQL/Redis 运行状态 */
    AdminOverviewVO overview();

    /** 用户列表（可按用户名/昵称搜索、按状态筛选） */
    PageResult<AdminUserVO> listUsers(long page, long size, String keyword, Integer status);

    /** 启用 / 禁用某个用户 */
    void updateUserStatus(Long userId, Integer status);

    /** 房间列表（可按状态筛选） */
    PageResult<AdminRoomVO> listRooms(long page, long size, String status);

    /** 强制关闭房间（把状态置为 FINISHED） */
    void closeRoom(Long roomId);

    /** 对局记录列表 */
    PageResult<AdminGameVO> listGames(long page, long size);

    /** 对局详情（含每个人的角色与胜负） */
    AdminGameDetailVO gameDetail(Long gameId);
}
