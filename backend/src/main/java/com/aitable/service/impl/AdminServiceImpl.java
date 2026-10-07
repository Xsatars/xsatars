package com.aitable.service.impl;

import com.aitable.common.BusinessException;
import com.aitable.common.PageResult;
import com.aitable.common.RedisKeys;
import com.aitable.common.UserContext;
import com.aitable.common.enums.GameStatus;
import com.aitable.common.enums.RoomStatus;
import com.aitable.common.enums.UserRole;
import com.aitable.entity.Game;
import com.aitable.entity.GameRecord;
import com.aitable.entity.Player;
import com.aitable.entity.Room;
import com.aitable.entity.User;
import com.aitable.mapper.GameMapper;
import com.aitable.mapper.GameRecordMapper;
import com.aitable.mapper.PlayerMapper;
import com.aitable.mapper.RoomMapper;
import com.aitable.mapper.UserMapper;
import com.aitable.service.AdminService;
import com.aitable.service.GameService;
import com.aitable.service.HealthService;
import com.aitable.vo.AdminGameDetailVO;
import com.aitable.vo.AdminGameVO;
import com.aitable.vo.AdminOverviewVO;
import com.aitable.vo.AdminRoomVO;
import com.aitable.vo.AdminUserVO;
import com.aitable.websocket.PageSessionRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理后台业务实现。
 */
@Service
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    /** 每页最多多少条，防止前端传个超大 size 把数据库拖死 */
    private static final long MAX_PAGE_SIZE = 100;

    private final UserMapper userMapper;
    private final RoomMapper roomMapper;
    private final PlayerMapper playerMapper;
    private final GameMapper gameMapper;
    private final GameRecordMapper gameRecordMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final HealthService healthService;
    private final GameService gameService;
    private final PageSessionRegistry pageSessionRegistry;

    public AdminServiceImpl(UserMapper userMapper, RoomMapper roomMapper, PlayerMapper playerMapper,
                            GameMapper gameMapper, GameRecordMapper gameRecordMapper,
                            StringRedisTemplate stringRedisTemplate, HealthService healthService,
                            GameService gameService, PageSessionRegistry pageSessionRegistry) {
        this.userMapper = userMapper;
        this.roomMapper = roomMapper;
        this.playerMapper = playerMapper;
        this.gameMapper = gameMapper;
        this.gameRecordMapper = gameRecordMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.healthService = healthService;
        this.gameService = gameService;
        this.pageSessionRegistry = pageSessionRegistry;
    }

    // ============================================================
    // ① 数据概览
    // ============================================================

    @Override
    public AdminOverviewVO overview() {
        AdminOverviewVO vo = new AdminOverviewVO();

        vo.setUserCount(count(userMapper.selectCount(null)));
        vo.setAdminCount(count(userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRole, UserRole.ADMIN.name()))));
        vo.setTodayNewUsers(count(userMapper.selectCount(
                new LambdaQueryWrapper<User>().ge(User::getCreatedAt, LocalDate.now().atStartOfDay()))));

        vo.setRoomCount(count(roomMapper.selectCount(null)));
        vo.setWaitingRoomCount(countByRoomStatus(RoomStatus.WAITING));
        vo.setPlayingRoomCount(countByRoomStatus(RoomStatus.PLAYING));
        vo.setFinishedRoomCount(countByRoomStatus(RoomStatus.FINISHED));

        vo.setGameCount(count(gameMapper.selectCount(null)));
        vo.setFinishedGameCount(count(gameMapper.selectCount(
                new LambdaQueryWrapper<Game>().eq(Game::getStatus, GameStatus.FINISHED.name()))));

        vo.setOnlineCount(countOnline());
        vo.setSystem(healthService.check());
        vo.setGeneratedAt(LocalDateTime.now());
        return vo;
    }

    private long countByRoomStatus(RoomStatus status) {
        return count(roomMapper.selectCount(new LambdaQueryWrapper<Room>().eq(Room::getStatus, status.name())));
    }

    /** 在线人数：当前有多少个账号有有效页面连接 */
    private long countOnline() {
        try {
            return pageSessionRegistry.count();
        } catch (Exception e) {
            log.warn("统计在线人数失败: {}", e.getMessage());
            return 0;
        }
    }

    private long count(Long value) {
        return value == null ? 0 : value;
    }

    // ============================================================
    // ② 用户管理
    // ============================================================

    @Override
    public PageResult<AdminUserVO> listUsers(long page, long size, String keyword, Integer status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, kw).or().like(User::getNickname, kw));
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getId);

        Page<User> result = userMapper.selectPage(newPage(page, size), wrapper);
        List<AdminUserVO> list = result.getRecords().stream().map(AdminUserVO::from).collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), page, normalizeSize(size));
    }

    @Override
    @Transactional
    public void updateUserStatus(Long userId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值只能是 0（禁用）或 1（启用）");
        }
        // 不允许把自己禁用，否则管理员会把自己锁在门外
        if (userId.equals(UserContext.getUserId()) && status == 0) {
            throw new BusinessException("不能禁用当前登录的管理员账号");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        log.info("管理员 {}({}) 将用户 {}({}) 状态改为 {}",
                UserContext.getUserId(), "admin", user.getUsername(), user.getId(), status);
    }

    // ============================================================
    // ③ 房间管理
    // ============================================================

    @Override
    public PageResult<AdminRoomVO> listRooms(long page, long size, String status) {
        LambdaQueryWrapper<Room> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(Room::getStatus, status.trim());
        }
        wrapper.orderByDesc(Room::getId);

        Page<Room> result = roomMapper.selectPage(newPage(page, size), wrapper);
        List<Room> rooms = result.getRecords();
        List<AdminRoomVO> list = new ArrayList<>();

        if (!rooms.isEmpty()) {
            List<Long> roomIds = rooms.stream().map(Room::getId).collect(Collectors.toList());
            List<Long> ownerIds = rooms.stream().map(Room::getOwnerUserId).distinct().collect(Collectors.toList());

            // 一次查出所有席位，再按房间分组统计人数（避免每个房间查一次库）
            List<Player> players = playerMapper.selectList(
                    new LambdaQueryWrapper<Player>().in(Player::getRoomId, roomIds));
            Map<Long, Long> countMap = players.stream()
                    .collect(Collectors.groupingBy(Player::getRoomId, Collectors.counting()));

            Map<Long, String> ownerMap = new HashMap<>();
            for (User owner : userMapper.selectBatchIds(ownerIds)) {
                ownerMap.put(owner.getId(), owner.getNickname());
            }

            for (Room room : rooms) {
                AdminRoomVO vo = AdminRoomVO.from(room);
                vo.setPlayerCount(countMap.getOrDefault(room.getId(), 0L).intValue());
                vo.setOwnerNickname(ownerMap.get(room.getOwnerUserId()));
                list.add(vo);
            }
        }

        return PageResult.of(list, result.getTotal(), page, normalizeSize(size));
    }

    @Override
    @Transactional
    public void closeRoom(Long roomId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        if (RoomStatus.FINISHED.name().equals(room.getStatus())) {
            throw new BusinessException("房间已经是结束状态");
        }

        // 房间里还有正在进行的对局时，要把它一起终止——
        // 否则对局记录会一直显示"进行中"，内存里的对局也还在空转。
        Long gameId = room.getCurrentGameId();
        if (gameId != null) {
            gameService.forceCloseGame(gameId);
        }

        room.setStatus(RoomStatus.FINISHED.name());
        room.setCurrentGameId(null);
        roomMapper.updateById(room);
        log.info("管理员 {} 强制关闭了房间 {}", UserContext.getUserId(), roomId);
    }

    // ============================================================
    // ④ 游戏记录
    // ============================================================

    @Override
    public PageResult<AdminGameVO> listGames(long page, long size) {
        Page<Game> result = gameMapper.selectPage(newPage(page, size),
                new LambdaQueryWrapper<Game>().orderByDesc(Game::getId));
        List<Game> games = result.getRecords();
        List<AdminGameVO> list = new ArrayList<>();

        if (!games.isEmpty()) {
            List<Long> gameIds = games.stream().map(Game::getId).collect(Collectors.toList());
            List<Long> roomIds = games.stream().map(Game::getRoomId).distinct().collect(Collectors.toList());

            Map<Long, String> roomNoMap = new HashMap<>();
            for (Room room : roomMapper.selectBatchIds(roomIds)) {
                roomNoMap.put(room.getId(), room.getRoomNo());
            }

            List<GameRecord> records = gameRecordMapper.selectList(
                    new LambdaQueryWrapper<GameRecord>().in(GameRecord::getGameId, gameIds));
            Map<Long, Long> countMap = records.stream()
                    .collect(Collectors.groupingBy(GameRecord::getGameId, Collectors.counting()));

            for (Game game : games) {
                AdminGameVO vo = AdminGameVO.from(game);
                vo.setRoomNo(roomNoMap.get(game.getRoomId()));
                vo.setPlayerCount(countMap.getOrDefault(game.getId(), 0L).intValue());
                list.add(vo);
            }
        }

        return PageResult.of(list, result.getTotal(), page, normalizeSize(size));
    }

    @Override
    public AdminGameDetailVO gameDetail(Long gameId) {
        Game game = gameMapper.selectById(gameId);
        if (game == null) {
            throw new BusinessException("对局不存在");
        }

        AdminGameVO gameVO = AdminGameVO.from(game);
        Room room = roomMapper.selectById(game.getRoomId());
        if (room != null) {
            gameVO.setRoomNo(room.getRoomNo());
        }

        List<GameRecord> records = gameRecordMapper.selectList(
                new LambdaQueryWrapper<GameRecord>().eq(GameRecord::getGameId, gameId).orderByAsc(GameRecord::getSeatNo));

        Map<Long, String> nicknames = new HashMap<>();
        if (!records.isEmpty()) {
            List<Long> userIds = records.stream().map(GameRecord::getUserId).distinct().collect(Collectors.toList());
            for (User user : userMapper.selectBatchIds(userIds)) {
                nicknames.put(user.getId(), user.getNickname());
            }
        }

        List<AdminGameDetailVO.PlayerResult> players = records.stream()
                .map(r -> AdminGameDetailVO.PlayerResult.from(r, nicknames.get(r.getUserId())))
                .collect(Collectors.toList());

        gameVO.setPlayerCount(players.size());

        AdminGameDetailVO detail = new AdminGameDetailVO();
        detail.setGame(gameVO);
        detail.setPlayers(players);
        return detail;
    }

    // ============================================================
    // 工具
    // ============================================================

    private <T> Page<T> newPage(long page, long size) {
        return new Page<>(Math.max(page, 1), normalizeSize(size));
    }

    private long normalizeSize(long size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
