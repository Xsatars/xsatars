package com.aitable.game.core;

import com.aitable.common.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 正在进行的对局管理器（存在内存里）。
 *
 * <p>为什么放内存不放 Redis：V0.1 是单实例运行，对局状态读得极其频繁
 * （每发一条消息都要算视图），放内存最快也最简单。
 * 代价是<b>服务重启会丢掉正在进行的对局</b>——战绩已经落库了，不影响历史记录。</p>
 */
@Component
public class GameManager {

    private final Map<Long, GameRuntime> runtimes = new ConcurrentHashMap<>();

    public void register(GameRuntime runtime) {
        runtimes.put(runtime.getGameId(), runtime);
    }

    /** 找不到返回 null */
    public GameRuntime find(Long gameId) {
        return runtimes.get(gameId);
    }

    /** 找不到就抛业务异常 */
    public GameRuntime require(Long gameId) {
        GameRuntime runtime = runtimes.get(gameId);
        if (runtime == null) {
            throw new BusinessException("对局不存在或已经结束（服务重启后正在进行的对局会丢失）");
        }
        return runtime;
    }

    public void remove(Long gameId) {
        runtimes.remove(gameId);
    }

    /** 当前进行中的对局数量 */
    public int size() {
        return runtimes.size();
    }

    public Collection<GameRuntime> all() {
        return runtimes.values();
    }
}
