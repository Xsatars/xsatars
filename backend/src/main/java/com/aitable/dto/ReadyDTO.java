package com.aitable.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 准备 / 取消准备的请求参数。
 */
public class ReadyDTO {

    /** true=准备 false=取消准备 */
    @NotNull(message = "不能为空")
    private Boolean ready;

    public Boolean getReady() {
        return ready;
    }

    public void setReady(Boolean ready) {
        this.ready = ready;
    }
}
