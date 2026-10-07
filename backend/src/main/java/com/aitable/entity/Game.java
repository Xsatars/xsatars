package com.aitable.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 一局游戏（对应表 games）。
 *
 * <p>游戏开始时插入一行（status=PLAYING），游戏结束时更新这一行
 * （status=FINISHED、winner_camp、total_rounds、ended_at）。</p>
 */
@TableName("games")
public class Game {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private String gameType;

    private String boardCode;

    /** PLAYING / FINISHED */
    private String status;

    /** 一共进行了几轮（一个夜晚+一个白天算一轮） */
    private Integer totalRounds;

    /** WOLF=狼人胜 / GOOD=好人胜，未结束时为 null */
    private String winnerCamp;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

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
