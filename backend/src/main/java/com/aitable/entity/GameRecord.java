package com.aitable.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 一局游戏里某个玩家的结果（对应表 game_records）。
 *
 * <p>游戏结束时由 GameEngine 统一写入。角色到这时才公开记录，对局进行中不会写这张表。</p>
 */
@TableName("game_records")
public class GameRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long gameId;

    private Long userId;

    private Integer seatNo;

    /** 角色：WOLF / SEER / WITCH / VILLAGER */
    private String roleCode;

    /** 阵营：WOLF / GOOD */
    private String camp;

    /** 结果：WIN / LOSE */
    private String result;

    /** 结束时是否存活：1=存活 0=死亡 */
    private Integer survived;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
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

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getCamp() {
        return camp;
    }

    public void setCamp(String camp) {
        this.camp = camp;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Integer getSurvived() {
        return survived;
    }

    public void setSurvived(Integer survived) {
        this.survived = survived;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
