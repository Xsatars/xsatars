package com.aitable.websocket;

import com.aitable.service.GameService;
import com.aitable.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;

/**
 * 在线状态、掉线处理、以及"一个账号只允许一个页面在玩"。
 *
 * <p>页面标识走 STOMP 的自定义头 {@code x-session-key}（不用握手 attributes——
 * 实测在 CONNECTED 帧里拿不到那些 attributes）。</p>
 *
 * <p>真的掉线（这个账号已经没有任何页面）时，会<b>立刻</b>替他在对局里跳过当前该他做的操作，
 * 不让全桌干等 90 秒超时。</p>
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    /** 前端在 STOMP CONNECT 帧里带上的"本页面标识" */
    public static final String PAGE_KEY_HEADER = "x-session-key";

    private final GameService gameService;
    private final RoomService roomService;
    private final PageSessionRegistry pageSessionRegistry;
    private final WsEventPublisher publisher;

    public WebSocketEventListener(GameService gameService, RoomService roomService,
                                  PageSessionRegistry pageSessionRegistry, WsEventPublisher publisher) {
        this.gameService = gameService;
        this.roomService = roomService;
        this.pageSessionRegistry = pageSessionRegistry;
        this.publisher = publisher;
    }

    /** 收到 CONNECT 帧：登记连接、必要时顶掉旧页面 */
    @EventListener
    public void onConnect(SessionConnectEvent event) {
        Long userId = userIdOf(event.getUser());
        if (userId == null) {
            return;
        }
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String pageKey = accessor.getFirstNativeHeader(PAGE_KEY_HEADER);

        boolean replacedOtherPage = pageSessionRegistry.register(sessionId, userId, pageKey);
        if (replacedOtherPage) {
            publisher.sendToUser(userId, WsMessageType.SESSION_REPLACED,
                    Map.of("sessionKey", pageKey == null ? "" : pageKey),
                    "你的账号已在其他页面登录，当前页面将退出");
            log.info("用户 {} 在新页面登录，已通知旧页面退出", userId);
        }

        gameService.markOnline(userId);
        roomService.notifyUserOnlineChanged(userId);
        log.info("WebSocket 已连接: userId={}", userId);
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        Long userId = userIdOf(event.getUser());
        if (userId == null) {
            return;
        }
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        boolean reallyGone = pageSessionRegistry.unregister(accessor.getSessionId());

        // 只有"这个账号确实没有任何页面在玩了"才算离线
        // （被顶替掉的旧页面断开时，新页面还在，不能误判离线）
        if (!reallyGone) {
            log.info("WebSocket 断开（该账号仍有其它页面在线）: userId={}", userId);
            return;
        }

        gameService.markOffline(userId);
        roomService.notifyUserOnlineChanged(userId);
        log.info("WebSocket 已断开: userId={}", userId);
    }

    private Long userIdOf(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return null;
        }
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
