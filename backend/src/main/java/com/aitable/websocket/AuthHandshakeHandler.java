package com.aitable.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * 握手完成后，把拦截器里解析出的用户ID 变成 WebSocket 会话的 Principal。
 *
 * <p>有了 Principal，Spring 才知道"这条连接是谁"，
 * {@code /user/queue/**} 这种点对点推送才能准确送到人。</p>
 */
public class AuthHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Object userId = attributes.get(AuthHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null) {
            return null;
        }
        return new StompPrincipal(String.valueOf(userId));
    }
}
