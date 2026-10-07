package com.aitable.controller;

import com.aitable.common.Result;
import com.aitable.common.UserContext;
import com.aitable.game.core.GameView;
import com.aitable.service.GameService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对局查询接口。
 *
 * <p>开始游戏走 {@code POST /api/rooms/{roomId}/start}（房间维度更自然，
 * 因为开局前对局ID还不存在）。</p>
 *
 * <p>这个接口主要用于前端刷新页面 / 断线重连后把游戏状态拉回来。</p>
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    /** 查询我在这局里的视图（公共状态 + 我的私人信息） */
    @GetMapping("/{gameId}")
    public Result<GameView> get(@PathVariable Long gameId) {
        return Result.success(gameService.viewFor(gameId, UserContext.getUserId()));
    }

    /**
     * 查我当前有没有还没结束的对局。
     *
     * <p>中途退出（关页面、断网）后再回来，前端靠这个接口把人领回原来的对局。
     * 没有进行中的对局时 data 是 null。</p>
     */
    @GetMapping("/current")
    public Result<GameView> current() {
        return Result.success(gameService.currentGameFor(UserContext.getUserId()));
    }
}
