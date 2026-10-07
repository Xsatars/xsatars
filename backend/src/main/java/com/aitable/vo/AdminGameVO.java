package com.aitable.vo;

import com.aitable.entity.Game;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 管理后台 - 对局列表里的一行。
 */
public class AdminGameVO {

    private Long id;
    private Long roomId;
    /** 房间号（方便对照） */
    private String roomNo;
    private String gameType;
    private String boardCode;
    /** PLAYING / FINISHED */
    private String status;
    private Integer totalRounds;
    /** WOLF=狼人胜 GOOD=好人胜 */
    private String winnerCamp;
    /** 参与人数 */
    private Integer playerCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endedAt;

    public static AdminGameVO from(Game game) {
        AdminGameVO vo = new AdminGameVO();
        vo.setId(game.getId());
        vo.setRoomId(game.getRoomId());
        vo.setGameType(game.getGameType());
        vo.setBoardCode(game.getBoardCode());
        vo.setStatus(game.getStatus());
        vo.setTotalRounds(game.getTotalRounds());
        vo.setWinnerCamp(game.getWinnerCamp());
        vo.setStartedAt(game.getStartedAt());
        vo.setEndedAt(game.getEndedAt());
        return vo;
    }

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

    public String getRoomNo() {
        return roomNo;
    }

    public void setRoomNo(String roomNo) {
        this.roomNo = roomNo;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalRounds() {
        return totalRounds;
    }

    public void setTotalRounds(Integer totalRounds) {
        this.totalRounds = totalRounds;
    }

    public String getWinnerCamp() {
        return winnerCamp;
    }

    public void setWinnerCamp(String winnerCamp) {
        this.winnerCamp = winnerCamp;
    }

    public Integer getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(Integer playerCount) {
        this.playerCount = playerCount;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }
}
