package com.aitable.config;

import com.aitable.common.RedisKeys;
import com.aitable.common.enums.RoomStatus;
import com.aitable.entity.Player;
import com.aitable.entity.Room;
import com.aitable.mapper.PlayerMapper;
import com.aitable.mapper.RoomMapper;
import com.aitable.websocket.PageSessionRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 房间"没人了就收摊"。
 *
 * <p>房间里所有人都离线超过一段时间（默认 1 分钟），就把这个房间连座位一起删掉，
 * 释放资源——否则玩家随手建一堆房间然后走人，它们会永远留在数据库里。</p>
 *
 * <p>判断依据是"房间里还有没有人在线"（有没有 WebSocket 连接）。
 * 全员离线的起始时间记在 Redis 里，只要有人回来就会立刻清掉，不会误删。
 * 只处理<b>等待中</b>的房间，正在打的对局不碰（对局由游戏自己的超时机制处理）。</p>
 */
@Component
public class RoomIdleScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoomIdleScheduler.class);

    private final RoomMapper roomMapper;
    private final PlayerMapper playerMapper;
    private final PageSessionRegistry pageSessionRegistry;
    private final StringRedisTemplate stringRedisTemplate;

    /** 全员离线多久之后自动清理（秒）。默认 10 分钟 —— 太短会让玩家看个复盘回来房间就没了 */
    @Value("${ai-table.room.idle-close-seconds:600}")
    private long idleCloseSeconds;

    public RoomIdleScheduler(RoomMapper roomMapper, PlayerMapper playerMapper,
                             PageSessionRegistry pageSessionRegistry,
                             StringRedisTemplate stringRedisTemplate) {
        this.roomMapper = roomMapper;
        this.playerMapper = playerMapper;
        this.pageSessionRegistry = pageSessionRegistry;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Scheduled(fixedDelayString = "${ai-table.room.idle-check-interval-ms:30000}")
    public void closeIdleRooms() {
        List<Room> rooms = roomMapper.selectList(new LambdaQueryWrapper<Room>()
                .eq(Room::getStatus, RoomStatus.WAITING.name()));
        if (rooms.isEmpty()) {
            return;
        }
        for (Room room : rooms) {
            try {
                checkRoom(room);
            } catch (Exception e) {
                // 单个房间出问题不能影响其它房间的清理
                log.warn("检查房间 {} 时出错: {}", room.getId(), e.getMessage());
            }
        }
    }

    private void checkRoom(Room room) {
        List<Player> seats = playerMapper.selectList(
                new LambdaQueryWrapper<Player>().eq(Player::getRoomId, room.getId()));

        // AI 玩家没有 WebSocket 连接，永远"离线"——它们不算数，只统计真人
        List<Player> humans = new ArrayList<>();
        for (Player seat : seats) {
            if (!seat.isAiPlayer()) {
                humans.add(seat);
            }
        }

        // 一个人都没有的房间，直接删掉
        if (seats.isEmpty()) {
            removeRoom(room, "空房间");
            return;
        }
        // 只剩 AI 的房间：真人已经走光了，没必要留着
        if (humans.isEmpty()) {
            removeRoom(room, "只剩 AI 玩家");
            return;
        }

        String key = RedisKeys.roomOffline(room.getId());

        // 兜底：这个房间还挂着一个"当前对局"，说明有人正在玩（或刚打完在看结算面板），
        // 绝不能清理 —— 玩家看个复盘回来发现房间连座位都没了，那体验太糟。
        // 等对局彻底结束、房间复位之后，下一轮扫描自然会收拾它。
        if (room.getCurrentGameId() != null) {
            stringRedisTemplate.delete(key);   // 也顺手把离线计时清掉，重新算
            return;
        }

        boolean anyoneOnline = humans.stream()
                .anyMatch(seat -> seat.getUserId() != null
                        && pageSessionRegistry.hasSession(seat.getUserId()));

        if (anyoneOnline) {
            // 有人回来了，重新计时
            stringRedisTemplate.delete(key);
            return;
        }

        String since = stringRedisTemplate.opsForValue().get(key);
        long now = System.currentTimeMillis();
        if (since == null) {
            // 第一次发现全员离线，先记个时间，下一轮再看
            stringRedisTemplate.opsForValue().set(key, String.valueOf(now), Duration.ofHours(2));
            return;
        }

        long idleMillis;
        try {
            idleMillis = now - Long.parseLong(since);
        } catch (NumberFormatException e) {
            stringRedisTemplate.delete(key);
            return;
        }

        if (idleMillis >= idleCloseSeconds * 1000) {
            removeRoom(room, "全员离线 " + (idleMillis / 1000) + " 秒");
        }
    }

    private void removeRoom(Room room, String reason) {
        playerMapper.delete(new LambdaQueryWrapper<Player>().eq(Player::getRoomId, room.getId()));
        roomMapper.deleteById(room.getId());
        stringRedisTemplate.delete(RedisKeys.roomOffline(room.getId()));
        log.info("房间 {}（房间号 {}）已自动清理：{}", room.getId(), room.getRoomNo(), reason);
    }
}
