package com.aitable.vo;

import com.aitable.entity.GameRecord;

import java.util.List;

/**
 * 管理后台 - 对局详情：这一局里每个人的角色和结果。
 *
 * <p>注意：角色只在<b>对局结束后</b>才有记录（存 game_records），
 * 对局进行中的角色在内存里，管理后台看不到——这是刻意的，
 * 防止管理员在游戏进行中偷看身份。</p>
 */
public class AdminGameDetailVO {

    private AdminGameVO game;

    private List<PlayerResult> players;

    /** 单个玩家在这局里的结果 */
    public static class PlayerResult {

        private Long userId;
        private String nickname;
        private Integer seatNo;
        /** WOLF / SEER / WITCH / VILLAGER */
        private String roleCode;
        /** WOLF / GOOD */
        private String camp;
        /** WIN / LOSE */
        private String result;
        /** 1=存活 0=死亡 */
        private Integer survived;

        public static PlayerResult from(GameRecord record, String nickname) {
            PlayerResult p = new PlayerResult();
            p.setUserId(record.getUserId());
            p.setNickname(nickname);
            p.setSeatNo(record.getSeatNo());
            p.setRoleCode(record.getRoleCode());
            p.setCamp(record.getCamp());
            p.setResult(record.getResult());
            p.setSurvived(record.getSurvived());
            return p;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
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
    }

    public AdminGameVO getGame() {
        return game;
    }

    public void setGame(AdminGameVO game) {
        this.game = game;
    }

    public List<PlayerResult> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerResult> players) {
        this.players = players;
    }
}
