package com.aitable.service.impl;

import com.aitable.common.BusinessException;
import com.aitable.common.GameConstants;
import com.aitable.common.UserContext;
import com.aitable.common.enums.RoomStatus;
import com.aitable.common.event.RoomChangedEvent;
import com.aitable.dto.CreateRoomDTO;
import com.aitable.entity.Player;
import com.aitable.entity.Room;
import com.aitable.entity.User;
import com.aitable.mapper.PlayerMapper;
import com.aitable.mapper.RoomMapper;
import com.aitable.mapper.UserMapper;
import com.aitable.service.RoomService;
import com.aitable.vo.PlayerVO;
import com.aitable.vo.RoomVO;
import com.aitable.websocket.PageSessionRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 房间业务实现。
 *
 * <p>这里只做"凑人"和"记座位"，不做任何游戏规则判断——
 * 规则是 GameEngine 的事。</p>
 */
@Service
public class RoomServiceImpl implements RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomServiceImpl.class);

    private final RoomMapper roomMapper;
    private final PlayerMapper playerMapper;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PageSessionRegistry pageSessionRegistry;

    public RoomServiceImpl(RoomMapper roomMapper, PlayerMapper playerMapper, UserMapper userMapper,
                           ApplicationEventPublisher eventPublisher, PageSessionRegistry pageSessionRegistry) {
        this.roomMapper = roomMapper;
        this.playerMapper = playerMapper;
        this.userMapper = userMapper;
        this.eventPublisher = eventPublisher;
        this.pageSessionRegistry = pageSessionRegistry;
    }

    @Override
    public void notifyUserOnlineChanged(Long userId) {
        List<Player> seats = playerMapper.selectList(
                new LambdaQueryWrapper<Player>().eq(Player::getUserId, userId));
        for (Player seat : seats) {
            Room room = roomMapper.selectById(seat.getRoomId());
            if (room != null && !RoomStatus.FINISHED.name().equals(room.getStatus())) {
                publishRoomChanged(room);
            }
        }
    }

    /**
     * 这个人现在在线吗。
     *
     * <p>依据是"他有没有一个有效的页面连接"（{@link PageSessionRegistry}），
     * 而不是自己再存一份标记——否则一个人开两个页面时，关掉其中一个就会误判离线。</p>
     */
    private boolean isOnline(Long userId) {
        return pageSessionRegistry.hasSession(userId);
    }

    @Override
    @Transactional
    public RoomVO createRoom(CreateRoomDTO dto) {
        Long userId = UserContext.getUserId();
        User owner = userMapper.selectById(userId);
        if (owner == null) {
            throw new BusinessException("用户不存在");
        }

        Room room = new Room();
        room.setRoomNo(generateRoomNo());
        room.setName(StringUtils.hasText(dto == null ? null : dto.getName())
                ? dto.getName().trim()
                : owner.getNickname() + " 的狼人杀房间");
        room.setOwnerUserId(userId);
        room.setGameType(GameConstants.GAME_TYPE_WEREWOLF);
        room.setBoardCode(GameConstants.BOARD_WEREWOLF_6);
        room.setMaxPlayers(GameConstants.WEREWOLF_6_PLAYER_COUNT);
        room.setStatus(RoomStatus.WAITING.name());
        roomMapper.insert(room);

        // 创建者直接坐 1 号座位；房主不需要点准备，直接给个"已就绪"，省得前端特判
        Player player = new Player();
        player.setRoomId(room.getId());
        player.setUserId(userId);
        player.setSeatNo(1);
        player.setReady(1);
        playerMapper.insert(player);

        log.info("用户 {} 创建了房间 {}（房间号 {}）", userId, room.getId(), room.getRoomNo());
        return publishAndBuild(room);
    }

    @Override
    @Transactional
    public RoomVO joinRoom(String roomIdOrNo) {
        Long userId = UserContext.getUserId();
        Room room = resolveRoom(roomIdOrNo);

        if (!RoomStatus.WAITING.name().equals(room.getStatus())) {
            throw new BusinessException("房间已开始游戏或已结束，无法加入");
        }

        // 已经在房间里：直接返回，不重复占座（前端刷新/重连时会发生）
        Player exists = playerMapper.selectOne(new LambdaQueryWrapper<Player>()
                .eq(Player::getRoomId, room.getId())
                .eq(Player::getUserId, userId));
        if (exists != null) {
            return toRoomVO(room);
        }

        List<Player> players = listPlayers(room.getId());
        if (players.size() >= room.getMaxPlayers()) {
            throw new BusinessException("房间已满（" + room.getMaxPlayers() + " 人）");
        }

        Player player = new Player();
        player.setRoomId(room.getId());
        player.setUserId(userId);
        player.setSeatNo(nextFreeSeat(players, room.getMaxPlayers()));
        player.setReady(0);
        playerMapper.insert(player);

        log.info("用户 {} 加入房间 {}，坐在 {} 号位", userId, room.getId(), player.getSeatNo());
        return publishAndBuild(room);
    }

    @Override
    public RoomVO getRoom(String roomIdOrNo) {
        return toRoomVO(resolveRoom(roomIdOrNo));
    }

    @Override
    @Transactional
    public void leaveRoom(Long roomId, Long playerId) {
        Long userId = UserContext.getUserId();

        Player player;
        if (playerId != null) {
            player = playerMapper.selectById(playerId);
        } else {
            // 没传 playerId 时（WebSocket 走这条路径），按"当前用户在这个房间的席位"来找
            player = playerMapper.selectOne(new LambdaQueryWrapper<Player>()
                    .eq(Player::getRoomId, roomId)
                    .eq(Player::getUserId, userId));
        }
        if (player == null || !player.getRoomId().equals(roomId)) {
            throw new BusinessException("该玩家不在此房间中");
        }
        if (!player.getUserId().equals(userId)) {
            throw new BusinessException("只能让自己离开房间");
        }

        playerMapper.deleteById(player.getId());
        log.info("用户 {} 离开房间 {}（原 {} 号位）", userId, roomId, player.getSeatNo());

        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            return;
        }

        List<Player> remain = listPlayers(roomId);
        if (remain.isEmpty()) {
            // 房间没人了，标记结束，不再占用房间号
            room.setStatus(RoomStatus.FINISHED.name());
            roomMapper.updateById(room);
            log.info("房间 {} 已无人，标记为 FINISHED", roomId);
            publishRoomChanged(room);
            return;
        }

        // 房主走了：把房主转给座位号最小的其他人
        if (room.getOwnerUserId().equals(userId)) {
            Player nextOwner = remain.get(0);
            room.setOwnerUserId(nextOwner.getUserId());
            roomMapper.updateById(room);
            log.info("房间 {} 房主转交给用户 {}", roomId, nextOwner.getUserId());
        }
        publishRoomChanged(room);
    }

    @Override
    @Transactional
    public RoomVO kickPlayer(Long roomId, Long playerId) {
        Long userId = UserContext.getUserId();
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        if (!room.getOwnerUserId().equals(userId)) {
            throw new BusinessException("只有房主可以踢人");
        }
        if (!RoomStatus.WAITING.name().equals(room.getStatus())) {
            throw new BusinessException("游戏已经开始了，不能再踢人");
        }

        Player player = playerMapper.selectById(playerId);
        if (player == null || !player.getRoomId().equals(roomId)) {
            throw new BusinessException("该玩家不在此房间中");
        }
        if (player.getUserId().equals(userId)) {
            throw new BusinessException("不能踢自己，想离开请点「退出房间」");
        }

        playerMapper.deleteById(player.getId());
        log.info("房主 {} 把用户 {} 踢出了房间 {}", userId, player.getUserId(), roomId);

        return publishAndBuild(room);
    }

    // ============================================================
    // 内部方法
    // ============================================================

    @Override
    @Transactional
    public RoomVO setReady(String roomIdOrNo, boolean ready) {
        Long userId = UserContext.getUserId();
        Room room = resolveRoom(roomIdOrNo);

        if (!RoomStatus.WAITING.name().equals(room.getStatus())) {
            throw new BusinessException("房间已经开始游戏了");
        }
        if (room.getOwnerUserId().equals(userId)) {
            throw new BusinessException("房主不需要准备，直接点开始游戏即可");
        }

        Player player = playerMapper.selectOne(new LambdaQueryWrapper<Player>()
                .eq(Player::getRoomId, room.getId())
                .eq(Player::getUserId, userId));
        if (player == null) {
            throw new BusinessException("你不在这个房间里");
        }

        player.setReady(ready ? 1 : 0);
        playerMapper.updateById(player);
        log.info("用户 {} 在房间 {} {}", userId, room.getId(), ready ? "已准备" : "取消准备");

        return publishAndBuild(room);
    }

    /**
     * 把路径参数解析成房间。
     *
     * <p>参数既可以传 6 位房间号（玩家输入的那个），也可以传房间ID，
     * 前端只需要用房间号即可。</p>
     */
    private Room resolveRoom(String roomIdOrNo) {
        if (!StringUtils.hasText(roomIdOrNo)) {
            throw new BusinessException("房间不存在");
        }
        String key = roomIdOrNo.trim();

        if (key.matches("\\d{" + GameConstants.ROOM_NO_LENGTH + "}")) {
            Room byNo = roomMapper.selectOne(new LambdaQueryWrapper<Room>().eq(Room::getRoomNo, key));
            if (byNo != null) {
                return byNo;
            }
        }
        if (key.matches("\\d+")) {
            Room byId = roomMapper.selectById(Long.valueOf(key));
            if (byId != null) {
                return byId;
            }
        }
        throw new BusinessException("房间不存在");
    }

    /** 生成一个没被占用的 6 位房间号 */
    private String generateRoomNo() {
        for (int i = 0; i < 30; i++) {
            String no = String.format("%06d", 100000 + ThreadLocalRandom.current().nextInt(900000));
            Long count = roomMapper.selectCount(new LambdaQueryWrapper<Room>().eq(Room::getRoomNo, no));
            if (count == null || count == 0) {
                return no;
            }
        }
        throw new BusinessException("房间号生成失败，请重试");
    }

    private List<Player> listPlayers(Long roomId) {
        return playerMapper.selectList(new LambdaQueryWrapper<Player>()
                .eq(Player::getRoomId, roomId)
                .orderByAsc(Player::getSeatNo));
    }

    /** 取最小的空座位号；理论上调用前已经确认没满员 */
    private int nextFreeSeat(List<Player> players, int maxPlayers) {
        for (int seat = 1; seat <= maxPlayers; seat++) {
            final int current = seat;
            boolean taken = players.stream().anyMatch(p -> p.getSeatNo() != null && p.getSeatNo() == current);
            if (!taken) {
                return seat;
            }
        }
        throw new BusinessException("房间已满");
    }

    /** 构造房间详情并发出"房间已变化"事件（WebSocket 层收到后会广播给房间里的人） */
    private RoomVO publishAndBuild(Room room) {
        RoomVO vo = toRoomVO(room);
        eventPublisher.publishEvent(new RoomChangedEvent(room.getId(), vo));
        return vo;
    }

    private void publishRoomChanged(Room room) {
        eventPublisher.publishEvent(new RoomChangedEvent(room.getId(), toRoomVO(room)));
    }

    private RoomVO toRoomVO(Room room) {
        List<Player> players = listPlayers(room.getId());

        RoomVO vo = new RoomVO();
        vo.setId(room.getId());
        vo.setRoomNo(room.getRoomNo());
        vo.setName(room.getName());
        vo.setOwnerUserId(room.getOwnerUserId());
        vo.setGameType(room.getGameType());
        vo.setBoardCode(room.getBoardCode());
        vo.setMaxPlayers(room.getMaxPlayers());
        vo.setStatus(room.getStatus());
        vo.setCurrentGameId(room.getCurrentGameId());
        vo.setPlayerCount(players.size());

        // 一次性把用到的用户查出来，避免每个玩家查一次库
        Map<Long, User> userMap = new HashMap<>();
        if (!players.isEmpty()) {
            List<Long> userIds = new ArrayList<>();
            for (Player p : players) {
                userIds.add(p.getUserId());
            }
            for (User u : userMapper.selectBatchIds(userIds)) {
                userMap.put(u.getId(), u);
            }
        }

        List<PlayerVO> playerVOs = new ArrayList<>();
        for (Player p : players) {
            PlayerVO pv = new PlayerVO();
            pv.setPlayerId(p.getId());
            pv.setUserId(p.getUserId());
            pv.setSeatNo(p.getSeatNo());
            pv.setReady(p.getReady() != null && p.getReady() == 1);
            pv.setOwner(p.getUserId().equals(room.getOwnerUserId()));
            User u = userMap.get(p.getUserId());
            if (u != null) {
                pv.setNickname(u.getNickname());
                pv.setAvatar(u.getAvatar());
            }
            // 在线状态：WebSocket 还连着就算在线
            pv.setOnline(isOnline(p.getUserId()));
            playerVOs.add(pv);
        }
        vo.setPlayers(playerVOs);

        // 开局条件：人满 + 所有人都准备了（房主创建房间时就是"已就绪"）
        boolean allReady = players.stream()
                .allMatch(p -> p.getReady() != null && p.getReady() == 1);
        vo.setCanStart(RoomStatus.WAITING.name().equals(room.getStatus())
                && players.size() == room.getMaxPlayers()
                && allReady);
        return vo;
    }
}
