package com.aitable.websocket;

import com.aitable.common.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手鉴权。
 *
 * <p>浏览器没法给 WebSocket 请求加自定义请求头，所以 token 通过地址传：</p>
 * <pre>
 * ws://localhost:8080/ws?token=登录拿到的token
 * </pre>
 *
 * <p>token 无效或过期就直接拒绝握手（HTTP 401）。
 * "哪个页面"这个信息走 STOMP 的自定义头（见 {@link WebSocketEventListener}）。</p>
 */
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthHandshakeInterceptor.class);

    /** 校验通过后，把用户ID放进 attributes 的 key */
    public static final String ATTR_USER_ID = "userId";

    private static final String TOKEN_PARAM = "token";

    private final StringRedisTemplate stringRedisTemplate;

    public AuthHandshakeInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (token == null) {
            log.warn("WebSocket 握手被拒：没有带 token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String userId = stringRedisTemplate.opsForValue().get(RedisKeys.token(token));
        if (userId == null) {
            log.warn("WebSocket 握手被拒：token 无效或已过期");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(ATTR_USER_ID, Long.valueOf(userId));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 不需要做什么
    }

    /** 优先取 ?token=xxx，其次取 Authorization 头 */
    private String resolveToken(ServerHttpRequest request) {
        String fromQuery = resolveQueryParam(request, TOKEN_PARAM);
        if (fromQuery != null) {
            return fromQuery;
        }
        String header = request.getHeaders().getFirst("Authorization");
        if (header != null && !header.isBlank()) {
            return header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
        }
        return null;
    }

    /** 从 URL 查询串里取一个参数 */
    private String resolveQueryParam(ServerHttpRequest request, String name) {
        String query = request.getURI().getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && name.equals(pair.substring(0, idx))) {
                String value = pair.substring(idx + 1);
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}
