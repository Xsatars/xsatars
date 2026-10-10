package com.aitable.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 房间（对应表 rooms）。
 *
 * <p>房间状态取值见 {@link com.aitable.common.enums.RoomStatus}。</p>
 */
@TableName("rooms")
public class Room {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 房间号，玩家输入这个加入房间，6 位数字 */
    private String roomNo;

    /** 房间名 */
    private String name;

    /** 房主用户ID */
    private Long ownerUserId;

    /** 游戏类型，V0.1 固定 WEREWOLF */
    private String gameType;

    /** 板子：WEREWOLF_6（6 人局）/ WEREWOLF_8（8 人局） */
    private String boardCode;

    /**
     * 这局的 AI 用哪个"脑子"：{@code rule}（规则）或 {@code llm}（大模型）。
     *
     * <p>房主可以在房间里随时切换，<b>下一局生效</b> —— 不让它在对局中途换脑子，
     * 否则同一个 AI 前后半局行为不一致，玩家会觉得精神分裂。</p>
     */
    private String aiBrain;

    /** 最大玩家数，V0.1 固定 6 */
    private Integer maxPlayers;

    /** WAITING / PLAYING / FINISHED */
    private String status;

    /** 当前对局ID（游戏进行中才有值） */
    private Long currentGameId;

    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoomNo() {
        return roomNo;
    }

    public void setRoomNo(String roomNo) {
        this.roomNo = roomNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public String getBoardCode() {
        return boardCode;
    }

    public void setBoardCode(String boardCode) {
        this.boardCode = boardCode;
    }

    public String getAiBrain() {
        return aiBrain;
    }

    public void setAiBrain(String aiBrain) {
        this.aiBrain = aiBrain;
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCurrentGameId() {
        return currentGameId;
    }

    public void setCurrentGameId(Long currentGameId) {
        this.currentGameId = currentGameId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
