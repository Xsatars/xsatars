package com.aitable.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 管理后台 - 修改用户状态（启用/禁用）。
 */
public class UpdateUserStatusDTO {

    /** 1=启用 0=禁用 */
    @NotNull(message = "不能为空")
    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
