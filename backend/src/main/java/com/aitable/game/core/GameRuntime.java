package com.aitable.game.core;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 一局游戏在内存里的运行时。
 *
 * <p>为什么需要它：WebSocket 消息是<b>多个线程并发</b>处理的，一局里可能好几个人
 * 同时提交行动（比如 6 个人同时点投票）。如果并发读写状态就会出错
 * （重复落库、ConcurrentModificationException）。这里给每局配一把锁，
 * 所有"读状态 + 改状态 + 生成消息"都在锁内串行完成，网络发送放到锁外。</p>
 */
public class GameRuntime {

    private final Long gameId;

    private final Long roomId;

    private final GameEngine engine;

    private final ReentrantLock lock = new ReentrantLock();

    /** 是否已经有人处理过"本局结束"的收尾动作 */
    private final AtomicBoolean finished = new AtomicBoolean(false);

    public GameRuntime(Long gameId, Long roomId, GameEngine engine) {
        this.gameId = gameId;
        this.roomId = roomId;
        this.engine = engine;
    }

    /** 在锁内执行，保证同一局的读和写一个接一个地处理 */
    public <T> T locked(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 标记这一局已经结束。
     *
     * <p>只有<b>第一个</b>调用的线程会拿到 true，其它线程拿到 false。
     * 这样"写战绩"只会执行一次——否则几个人同时结束时会重复插一整套记录。</p>
     */
    public boolean markFinished() {
        return finished.compareAndSet(false, true);
    }

    public Long getGameId() {
        return gameId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public GameEngine getEngine() {
        return engine;
    }
}
