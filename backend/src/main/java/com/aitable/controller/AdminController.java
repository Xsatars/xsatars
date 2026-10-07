package com.aitable.controller;

import com.aitable.common.PageResult;
import com.aitable.common.Result;
import com.aitable.dto.UpdateUserStatusDTO;
import com.aitable.service.AdminService;
import com.aitable.vo.AdminGameDetailVO;
import com.aitable.vo.AdminGameVO;
import com.aitable.vo.AdminOverviewVO;
import com.aitable.vo.AdminRoomVO;
import com.aitable.vo.AdminUserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台接口。
 *
 * <p>整个 {@code /api/admin/**} 由 {@code AdminInterceptor} 拦住，
 * 只有 ADMIN 角色的用户能访问，普通玩家会收到 403。</p>
 *
 * <p>当前有四块：数据概览、用户管理、房间管理、游戏记录。
 * AI 玩家管理、AI 决策记录、Prompt 管理、AI 运行状态等 V0.3 之后再补。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ---------- ① 数据概览 ----------

    @GetMapping("/overview")
    public Result<AdminOverviewVO> overview() {
        return Result.success(adminService.overview());
    }

    // ---------- ② 用户管理 ----------

    @GetMapping("/users")
    public Result<PageResult<AdminUserVO>> listUsers(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(adminService.listUsers(page, size, keyword, status));
    }

    @PutMapping("/users/{userId}/status")
    public Result<Void> updateUserStatus(@PathVariable Long userId,
                                         @Valid @RequestBody UpdateUserStatusDTO dto) {
        adminService.updateUserStatus(userId, dto.getStatus());
        return Result.success("已更新", null);
    }

    // ---------- ③ 房间管理 ----------

    @GetMapping("/rooms")
    public Result<PageResult<AdminRoomVO>> listRooms(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status) {
        return Result.success(adminService.listRooms(page, size, status));
    }

    @PostMapping("/rooms/{roomId}/close")
    public Result<Void> closeRoom(@PathVariable Long roomId) {
        adminService.closeRoom(roomId);
        return Result.success("房间已关闭", null);
    }

    // ---------- ④ 游戏记录 ----------

    @GetMapping("/games")
    public Result<PageResult<AdminGameVO>> listGames(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(adminService.listGames(page, size));
    }

    @GetMapping("/games/{gameId}")
    public Result<AdminGameDetailVO> gameDetail(@PathVariable Long gameId) {
        return Result.success(adminService.gameDetail(gameId));
    }
}
