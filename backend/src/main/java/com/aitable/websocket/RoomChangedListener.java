package com.aitable.websocket;

import com.aitable.common.event.RoomChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 把"房间变化事件"转成 WebSocket 广播。
 *
 * <p>监听时机是<b>事务提交之后</b>：这样如果 Service 那边最后出了错回滚，
 * 玩家就不会收到一条根本没生效的房间更新。
 * {@code fallbackExecution = true} 是为了在"没有事务"的场景下也能正常广播。</p>
 */
@Component
public class RoomChangedListener {

    private final WsEventPublisher publisher;

    public RoomChangedListener(WsEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRoomChanged(RoomChangedEvent event) {
        publisher.broadcastToRoom(event.getRoomId(), WsMessageType.ROOM_UPDATE, event.getRoom());
    }
}
