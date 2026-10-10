package com.aitable.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 房间里的玩家席位（对应表 players）。
 *
 * <p>一个用户在一个房间里只有一条记录，座位号 1..6。
 * 注意：这里<b>不存角色</b>——角色属于对局状态，放在内存 GameState 里，
 * 一局结束后才写进 game_records。</p>
 */
@TableName("players")
public class Player {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private Long userId;

    /** 座位号 1..6 */
    private Integer seatNo;

    /** 是否已准备：1=已准备 0=未准备 */
    private Integer ready;

    /**
     * 是否 AI 玩家：1=AI 0=真人。
     *
     * <p>AI 玩家没有真实账号，所以 {@code userId} 是 null，名字从 {@link #aiName} 取。</p>
     */
    private Integer isAi;

    /** AI 的名字（真人玩家用 users.nickname，这里是 null） */
    private String aiName;

    /** AI 的性格标识（对应 AiPersona 枚举，如 HOTHEAD / SNEAKY）；真人这里是 null */
    private String aiPersona;

    private LocalDateTime joinedAt;

    /** 离开房间的时间，未离开为 null */
    private LocalDateTime leftAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(Integer seatNo) {
        this.seatNo = seatNo;
    }

    public Integer getReady() {
        return ready;
    }

    public void setReady(Integer ready) {
        this.ready = ready;
    }

    /** 这个席位是不是 AI 玩家 */
    public boolean isAiPlayer() {
        return isAi != null && isAi == 1;
    }

    public Integer getIsAi() {
        return isAi;
    }

    public void setIsAi(Integer isAi) {
        this.isAi = isAi;
    }

    public String getAiName() {
        return aiName;
    }

    public void setAiName(String aiName) {
        this.aiName = aiName;
    }

    public String getAiPersona() {
        return aiPersona;
    }

    public void setAiPersona(String aiPersona) {
        this.aiPersona = aiPersona;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public LocalDateTime getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(LocalDateTime leftAt) {
        this.leftAt = leftAt;
    }
}
