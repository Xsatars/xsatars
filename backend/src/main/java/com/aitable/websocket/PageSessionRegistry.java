package com.aitable.websocket;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记录"哪个账号现在有哪些页面连着"。
 *
 * <p>同一个账号在两个页面同时打开时，<b>后来者顶替先来者</b>：
 * 旧页面会收到一条 {@code SESSION_REPLACED} 消息然后自动断开。
 * 这样就不会出现"两个页面同时操作同一个号"的混乱。</p>
 *
 * <p>每个页面打开时会生成一个随机标识（存在 sessionStorage 里），
 * 连 WebSocket 时通过 STOMP 的 {@code x-session-key} 头带上来，服务端靠它区分页面。</p>
 */
@Component
public class PageSessionRegistry {

    /** 连接ID → 这条连接是谁的 */
    private final Map<String, Long> sessionOwners = new ConcurrentHashMap<>();

    /** 用户ID → 当前有效页面的标识（用来判断"是不是同一个页面"） */
    private final Map<Long, String> userPages = new ConcurrentHashMap<>();

    /**
     * 登记一条新连接。
     *
     * @param pageKey 页面标识；没带就退化成用连接ID（那样每次重连都会被当成新页面）
     * @return true 表示顶替掉了另一个页面（需要通知旧页面断开）
     */
    public boolean register(String sessionId, Long userId, String pageKey) {
        if (sessionId == null || userId == null) {
            return false;
        }
        sessionOwners.put(sessionId, userId);
        String key = (pageKey == null || pageKey.isBlank()) ? sessionId : pageKey;
        String previous = userPages.put(userId, key);
        return previous != null && !previous.equals(key);
    }

    /**
     * 注销一条连接。
     *
     * @return true 表示这个账号已经没有任何连接了（真的离线）
     */
    public boolean unregister(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        Long userId = sessionOwners.remove(sessionId);
        if (userId == null) {
            return false;
        }
        boolean stillConnected = sessionOwners.containsValue(userId);
        if (!stillConnected) {
            userPages.remove(userId);
        }
        return !stillConnected;
    }

    /** 这个账号现在还有有效连接吗 */
    public boolean hasSession(Long userId) {
        return userId != null && userPages.containsKey(userId);
    }

    /** 当前在线人数 */
    public int count() {
        return userPages.size();
    }
}
