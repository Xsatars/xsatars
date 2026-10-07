package com.aitable.common.event;

import com.aitable.vo.RoomVO;

/**
 * 房间发生变化的事件（有人加入、离开、状态改变）。
 *
 * <p>为什么用事件而不是直接在 Service 里调 WebSocket 推送？
 * 因为房间有两个入口：REST 接口和 WebSocket 消息。
 * 如果只在其中一个入口推送，另一个入口的玩家就看不到变化。
 * 用事件的话，Service 只管"发公告"，谁需要听谁自己监听，
 * Service 不用认识 WebSocket 这个模块。</p>
 */
public class RoomChangedEvent {

    private final Long roomId;

    /** 变化后的房间详情，直接带着走，监听方不用再查一次库 */
    private final RoomVO room;

    public RoomChangedEvent(Long roomId, RoomVO room) {
        this.roomId = roomId;
        this.room = room;
    }

    public Long getRoomId() {
        return roomId;
    }

    public RoomVO getRoom() {
        return room;
    }
}
