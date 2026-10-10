package com.aitable.service;

import com.aitable.dto.CreateRoomDTO;
import com.aitable.vo.RoomVO;

/**
 * 房间相关业务。
 *
 * <p>注意：这里的房间只负责"凑齐人"和"记录座位"，
 * 真正的游戏规则（发牌、夜晚、白天、投票、胜负）全部在 GameEngine 里，
 * 房间不参与任何规则判断。</p>
 */
public interface RoomService {

    /** 创建房间，创建者自动坐 1 号座位，并成为房主 */
    RoomVO createRoom(CreateRoomDTO dto);

    /** 加入房间。已经在房间里时直接返回房间信息（幂等，方便前端刷新） */
    RoomVO joinRoom(String roomIdOrNo);

    /** 查询房间详情（含玩家列表） */
    RoomVO getRoom(String roomIdOrNo);

    /** 离开房间。如果离开的是房主，房主自动转给座位号最小的其他人 */
    void leaveRoom(Long roomId, Long playerId);

    /**
     * 房主把某个成员踢出房间。
     *
     * <p>只有房主能踢；不能踢自己（想走得用"离开房间"，那样房主会自动转交）。</p>
     */
    RoomVO kickPlayer(Long roomId, Long playerId);

    /**
     * 房主添加一个 AI 玩家补位。
     *
     * <p>AI 没有真实账号（user_id 为空），加进来就是「已就绪」——它不会点准备。
     * 房间满了或者已经开局就不能再加。</p>
     *
     * @param personaKey 指定性格（AiPersona 的枚举名，如 HOTHEAD）；
     *                   传空或无效值就自动挑一个这桌还没用过的
     */
    RoomVO addAiPlayer(Long roomId, String personaKey);

    /** 房主移除一个 AI 玩家（只能移除 AI；真人得用踢人） */
    RoomVO removeAiPlayer(Long roomId, Long playerId);

    /**
     * 房主切换 AI 的"脑子"：{@code rule}（规则 AI）或 {@code llm}（大模型 AI）。
     *
     * <p>切换<b>下一局生效</b> —— 不让它在对局中途换，否则同一个 AI
     * 前半局一个样、后半局一个样，玩家会觉得精神分裂。</p>
     */
    RoomVO updateAiBrain(Long roomId, String aiBrain);

    /**
     * 准备 / 取消准备。
     *
     * <p>房主不需要准备（他点"开始游戏"就开局），所以房主调这个会被拒绝。
     * 满员 + 除房主外所有人都准备了，房主才能开局。</p>
     */
    RoomVO setReady(String roomIdOrNo, boolean ready);

    /**
     * 某个人的在线状态变了（上线 / 掉线）。
     *
     * <p>把他所在的房间重新广播一遍，这样房间里其他人能立刻看到「离线」标记，
     * 而不是等下一次有人加入/离开才刷新。</p>
     */
    void notifyUserOnlineChanged(Long userId);
}
