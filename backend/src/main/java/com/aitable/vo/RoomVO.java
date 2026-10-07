package com.aitable.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 房间详情（返回给前端）。
 *
 * <p>房间页需要的信息基本都在这里：房间号、状态、座位列表、能不能开局。</p>
 */
public class RoomVO {

    private Long id;

    private String roomNo;

    private String name;

    private Long ownerUserId;

    private String gameType;

    private String boardCode;

    /** 最大人数（6 人局就是 6） */
    private Integer maxPlayers;

    /** 当前人数 */
    private Integer playerCount;

    /** WAITING / PLAYING / FINISHED */
    private String status;

    /** 当前进行中的对局ID，没有就是 null */
    private Long currentGameId;

    /** 玩家列表，按座位号排序 */
    private List<PlayerVO> players = new ArrayList<>();

    /** 是否满足开局条件（人数已满且房间处于等待状态）——前端据此决定"开始游戏"按钮是否可点 */
    private boolean canStart;

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

    public List<PlayerVO> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerVO> players) {
        this.players = players;
    }

    public boolean isCanStart() {
        return canStart;
    }

    public void setCanStart(boolean canStart) {
        this.canStart = canStart;
    }
}
