package com.aitable.websocket;

import java.security.Principal;

/**
 * WebSocket 连接对应的用户身份。
 *
 * <p>握手时用 token 换到 userId，包成这个对象交给 Spring。
 * 之后 {@code convertAndSendToUser(userId, ...)} 就能精准推送给某个人。</p>
 */
public class StompPrincipal implements Principal {

    private final String name;

    public StompPrincipal(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
