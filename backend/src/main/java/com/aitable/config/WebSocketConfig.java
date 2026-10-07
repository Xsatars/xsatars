package com.aitable.config;

import com.aitable.websocket.AuthHandshakeHandler;
import com.aitable.websocket.AuthHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置（STOMP 协议）。
 *
 * <h3>地址约定</h3>
 * <ul>
 *   <li>连接端点：{@code ws://localhost:8080/ws?token=登录拿到的token}</li>
 *   <li>公共频道：{@code /topic/room/{roomId}} —— 房间内所有人都能收到（座位变化等公开信息）</li>
 *   <li>私人频道：{@code /user/queue/game} —— 只有自己收到（自己的角色、查验结果等）</li>
 *   <li>客户端发消息：{@code /app/action}（统一入口，格式见 WsMessage）</li>
 * </ul>
 *
 * <h3>鉴权</h3>
 * <p>握手阶段就要带 token（浏览器无法给 WebSocket 加自定义请求头，所以放在地址参数里）。
 * token 无效直接拒绝连接，因此后面所有消息都能确定"是谁发的"。</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** 客户端连接地址 */
    public static final String ENDPOINT = "/ws";

    private final StringRedisTemplate stringRedisTemplate;

    public WebSocketConfig(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                // 开发环境放开跨域；上线时应改成具体域名
                .setAllowedOriginPatterns("*")
                .addInterceptors(new AuthHandshakeInterceptor(stringRedisTemplate))
                .setHandshakeHandler(new AuthHandshakeHandler());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 简单内存消息代理：/topic 用于广播，/queue 用于点对点
        registry.enableSimpleBroker("/topic", "/queue");
        // 客户端发消息的路由前缀
        registry.setApplicationDestinationPrefixes("/app");
        // 点对点消息前缀（配合 convertAndSendToUser 使用）
        registry.setUserDestinationPrefix("/user");
    }
}
