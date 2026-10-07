package com.aitable.vo;

import com.aitable.entity.Room;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 管理后台 - 房间列表里的一行。
 */
public class AdminRoomVO {

    private Long id;
    private String roomNo;
    private String name;
    private Long ownerUserId;
    private String ownerNickname;
    private String gameType;
    private String boardCode;
    private Integer maxPlayers;
    /** 当前人数 */
    private Integer playerCount;
    private String status;
    private Long currentGameId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public static AdminRoomVO from(Room room) {
        AdminRoomVO vo = new AdminRoomVO();
        vo.setId(room.getId());
        vo.setRoomNo(room.getRoomNo());
        vo.setName(room.getName());
        vo.setOwnerUserId(room.getOwnerUserId());
        vo.setGameType(room.getGameType());
        vo.setBoardCode(room.getBoardCode());
        vo.setMaxPlayers(room.getMaxPlayers());
        vo.setStatus(room.getStatus());
        vo.setCurrentGameId(room.getCurrentGameId());
        vo.setCreatedAt(room.getCreatedAt());
        return vo;
    }

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

    public String getOwnerNickname() {
        return ownerNickname;
    }

    public void setOwnerNickname(String ownerNickname) {
        this.ownerNickname = ownerNickname;
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

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public Integer getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(Integer playerCount) {
        this.playerCount = playerCount;
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
}
