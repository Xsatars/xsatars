package com.aitable.controller;

import com.aitable.common.Result;
import com.aitable.dto.CreateRoomDTO;
import com.aitable.dto.ReadyDTO;
import com.aitable.game.ai.AiPersona;
import com.aitable.game.werewolf.Board;
import com.aitable.service.GameService;
import com.aitable.service.RoomService;
import com.aitable.vo.AiPersonaVO;
import com.aitable.vo.BoardVO;
import com.aitable.vo.RoomVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
     * 房主添加一个 AI 玩家补位（一个人也能开局）。
     *
     * @param persona 想指定性格就传（如 HOTHEAD）；不传就自动挑一个这桌没用过的
     */
    @PostMapping("/{roomId}/ai")
    public Result<RoomVO> addAi(@PathVariable Long roomId,
                                @RequestParam(required = false) String persona) {
        RoomVO room = roomService.addAiPlayer(roomId, persona);
        return Result.success("已添加一个 AI 玩家", room);
    }

    /**
     * 所有可选的 AI 性格（给前端下拉框用）。
     *
     * <p>前端不硬编码这份列表，而是问后端要 —— 以后加性格只改后端一处，
     * 前端不用跟着发版。</p>
     */
    @GetMapping("/ai/personas")
    public Result<List<AiPersonaVO>> personas() {
        List<AiPersonaVO> list = new ArrayList<>();
        for (AiPersona persona : AiPersona.values()) {
            list.add(AiPersonaVO.from(persona));
        }
        return Result.success(list);
    }

    /**
     * 房主切换 AI 的脑子（规则 AI / 大模型 AI）。下一局生效。
     *
     * <p>请求体只要一个字段：{@code {"aiBrain": "rule"}} 或 {@code {"aiBrain": "llm"}}。
     * 用 Map 接是因为就这一个字段，专门建个 DTO 有点小题大做。</p>
     */
    @PutMapping("/{roomId}/ai-brain")
    public Result<RoomVO> updateAiBrain(@PathVariable Long roomId,
                                        @RequestBody(required = false) Map<String, String> body) {
        RoomVO room = roomService.updateAiBrain(roomId, body == null ? null : body.get("aiBrain"));
        return Result.success("AI 模式已切换，下一局生效", room);
    }

    /**
     * 有哪些板子可选（6 人局 / 8 人局）。
     *
     * <p>同样不让前端硬编码：以后加板子（比如 9 人局、白狼王局）只改后端一处。</p>
     */
    @GetMapping("/boards")
    public Result<List<BoardVO>> boards() {
        List<BoardVO> list = new ArrayList<>();
        for (Board board : Board.values()) {
            list.add(BoardVO.from(board));
        }
        return Result.success(list);
    }

    /** 房主移除一个 AI 玩家（只能移除 AI，真人要用踢人） */
    @DeleteMapping("/{roomId}/ai/{playerId}")
    public Result<RoomVO> removeAi(@PathVariable Long roomId, @PathVariable Long playerId) {
        RoomVO room = roomService.removeAiPlayer(roomId, playerId);
        return Result.success("已移除该 AI 玩家", room);
    }

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

    /**
     * 准备 / 取消准备（房主不需要准备，调这个会被拒绝）。
     *
     * <p>满员 + 除房主外所有人都准备了，房主才能开局。AI 加进来就是已就绪。</p>
     */
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
