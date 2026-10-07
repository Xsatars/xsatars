package com.aitable.controller;

import com.aitable.common.Result;
import com.aitable.dto.CreateRoomDTO;
import com.aitable.dto.ReadyDTO;
import com.aitable.service.GameService;
import com.aitable.service.RoomService;
import com.aitable.vo.RoomVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 房间接口。
 *
 * <p>路径里的 {@code {roomId}} 既可以传房间ID，也可以传 6 位房间号
 * （玩家输入的那个），前端用房间号就行。</p>
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final GameService gameService;

    public RoomController(RoomService roomService, GameService gameService) {
        this.roomService = roomService;
        this.gameService = gameService;
    }

    /** 创建房间（创建者自动成为房主并坐 1 号位） */
    @PostMapping
    public Result<RoomVO> create(@Valid @RequestBody(required = false) CreateRoomDTO dto) {
        return Result.success("房间创建成功", roomService.createRoom(dto));
    }

    /** 加入房间 */
    @PostMapping("/{roomId}/join")
    public Result<RoomVO> join(@PathVariable String roomId) {
        return Result.success("加入房间成功", roomService.joinRoom(roomId));
    }

    /** 查询房间详情（含座位列表） */
    @GetMapping("/{roomId}")
    public Result<RoomVO> get(@PathVariable String roomId) {
        return Result.success(roomService.getRoom(roomId));
    }

    /** 离开房间（只能让自己离开；房主离开时房主自动转移） */
    @DeleteMapping("/{roomId}/players/{playerId}")
    public Result<Void> leave(@PathVariable Long roomId, @PathVariable Long playerId) {
        roomService.leaveRoom(roomId, playerId);
        return Result.success("已离开房间", null);
    }

    /**
     * 准备 / 取消准备（房主不需要准备，调这个会被拒绝）。
     *
     * <p>满员 + 除房主外所有人都准备了，房主才能开局。</p>
     */
    /**
     * 房主把某个成员踢出房间。
     *
     * <p>只有房主能踢，而且只能踢别人（自己想走得用「离开房间」，那样房主会自动转交）。</p>
     */
    @PostMapping("/{roomId}/players/{playerId}/kick")
    public Result<RoomVO> kick(@PathVariable Long roomId, @PathVariable Long playerId) {
        RoomVO room = roomService.kickPlayer(roomId, playerId);
        return Result.success("已把该玩家移出房间", room);
    }

    @PostMapping("/{roomId}/ready")
    public Result<RoomVO> ready(@PathVariable String roomId, @Valid @RequestBody ReadyDTO dto) {
        RoomVO room = roomService.setReady(roomId, Boolean.TRUE.equals(dto.getReady()));
        return Result.success(Boolean.TRUE.equals(dto.getReady()) ? "已准备" : "已取消准备", room);
    }

    /**
     * 房主开始游戏。满员才能开局，开局后自动发身份牌并进入第一夜。
     *
     * @return 新对局的 ID
     */
    @PostMapping("/{roomId}/start")
    public Result<Long> start(@PathVariable Long roomId) {
        return Result.success("游戏开始", gameService.startGame(roomId));
    }
}
