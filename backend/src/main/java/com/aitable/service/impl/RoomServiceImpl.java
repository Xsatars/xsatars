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
import com.aitable.game.ai.AiBrainRouter;
import com.aitable.game.ai.AiPersona;
import com.aitable.game.werewolf.Board;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final AiBrainRouter aiBrainRouter;

    /**
     * 新建房间时 AI 默认用哪种脑子（跟全局配置 {@code ai-table.ai.brain} 一致）。
     *
     * <p>这里直接注字符串而不是注入 {@code AiConfig} —— 那是个配置类，
     * 注进来容易和 AI 那套 bean 形成绕来绕去的依赖，没必要。</p>
     */
    @Value("${ai-table.ai.brain:rule}")
    private String defaultAiBrain;

    public RoomServiceImpl(RoomMapper roomMapper, PlayerMapper playerMapper, UserMapper userMapper,
                           ApplicationEventPublisher eventPublisher, PageSessionRegistry pageSessionRegistry,
                           AiBrainRouter aiBrainRouter) {
        this.roomMapper = roomMapper;
        this.playerMapper = playerMapper;
        this.userMapper = userMapper;
        this.eventPublisher = eventPublisher;
        this.pageSessionRegistry = pageSessionRegistry;
        this.aiBrainRouter = aiBrainRouter;
    }

    @Override
    public RoomVO updateAiBrain(Long roomId, String aiBrain) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        Long userId = UserContext.getUserId();
        if (!room.getOwnerUserId().equals(userId)) {
            throw new BusinessException("只有房主可以切换 AI 模式");
        }
        // 只认这两个值，别的都当规则 AI（别让脏数据搞得 AI 不工作）
        String normalized = "llm".equalsIgnoreCase(aiBrain) ? "llm" : "rule";
        room.setAiBrain(normalized);
        roomMapper.updateById(room);
        log.info("房间 {} 的 AI 模式切换为 {}（下一局生效）", roomId, normalized);
        return toRoomVO(room);
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
        // 板子决定人数和角色构成：6 人局或 8 人局
        Board board = Board.byCode(dto == null ? null : dto.getBoardCode());
        room.setBoardCode(board.getCode());
        room.setMaxPlayers(board.getPlayerCount());
        // AI 用哪个脑子：默认跟全局配置一致，房主进房间后可以随时改（下一局生效）
        room.setAiBrain(defaultAiBrain);
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
        // AI 席位没有 userId，不让它走"离开房间"这条路（房主用「移除 AI」）
        if (player.getUserId() == null || !player.getUserId().equals(userId)) {
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

        // 房主走了：把房主转给座位号最小的**真人**（AI 当不了房主）
        if (room.getOwnerUserId().equals(userId)) {
            Player nextOwner = null;
            for (Player p : remain) {
                if (!p.isAiPlayer() && p.getUserId() != null) {
                    nextOwner = p;
                    break;
                }
            }
            if (nextOwner == null) {
                // 剩下的全是 AI，这房间没必要留着了
                room.setStatus(RoomStatus.FINISHED.name());
                roomMapper.updateById(room);
                log.info("房间 {} 只剩 AI 玩家，标记为 FINISHED", roomId);
            } else {
                room.setOwnerUserId(nextOwner.getUserId());
                roomMapper.updateById(room);
                log.info("房间 {} 房主转交给用户 {}（{} 号位）",
                        roomId, nextOwner.getUserId(), nextOwner.getSeatNo());
            }
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
        // AI 的 userId 是 null，不能让 null 参与比较；而且它们应该用「移除」而不是「踢出」
        if (player.getUserId() != null && player.getUserId().equals(userId)) {
            throw new BusinessException("不能踢自己，想离开请点「退出房间」");
        }

        playerMapper.deleteById(player.getId());
        log.info("房主 {} 把用户 {} 踢出了房间 {}", userId, player.getUserId(), roomId);

        return publishAndBuild(room);
    }

    @Override
    @Transactional
    public RoomVO addAiPlayer(Long roomId, String personaKey) {
        Long userId = UserContext.getUserId();
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        if (!room.getOwnerUserId().equals(userId)) {
            throw new BusinessException("只有房主可以添加 AI 玩家");
        }
        if (!RoomStatus.WAITING.name().equals(room.getStatus())) {
            throw new BusinessException("游戏已经开始了，不能再加人");
        }

        List<Player> seats = listPlayers(roomId);
        if (seats.size() >= room.getMaxPlayers()) {
            throw new BusinessException("房间已经满了");
        }

        // 挑一个空座位（座位号必须唯一）
        Set<Integer> usedSeats = new HashSet<>();
        Set<String> usedNames = new HashSet<>();
        for (Player seat : seats) {
            usedSeats.add(seat.getSeatNo());
            if (seat.isAiPlayer() && seat.getAiName() != null) {
                usedNames.add(seat.getAiName());
            }
        }
        int seatNo = 0;
        for (int i = 1; i <= room.getMaxPlayers(); i++) {
            if (!usedSeats.contains(i)) {
                seatNo = i;
                break;
            }
        }
        if (seatNo == 0) {
            throw new BusinessException("没有空座位了");
        }

        Player ai = new Player();
        ai.setRoomId(roomId);
        ai.setUserId(null);        // AI 没有真实账号
        ai.setSeatNo(seatNo);
        ai.setReady(1);            // AI 不需要点准备，直接就是已就绪
        ai.setIsAi(1);
        // 人设一起定下来：名字和性格是绑定的，一局中途不会变，
        // 这样玩家能形成印象（"那个急性子又开始了"）。
        // 房主点名要谁就听房主的；没点名或者这个性格已经被占，就自动挑一个没用过的。
        AiPersona persona = AiPersona.byKey(personaKey);
        if (persona == null || usedNames.contains(persona.getName())) {
            persona = AiPersona.pick(usedNames);
        }
        ai.setAiName(persona.getName());
        ai.setAiPersona(persona.getKey());
        playerMapper.insert(ai);
        log.info("房主 {} 在房间 {} 添加了 AI「{}」（{} 号位，人设：{}）",
                userId, roomId, ai.getAiName(), seatNo, persona.getTag());

        return publishAndBuild(room);
    }

    @Override
    @Transactional
    public RoomVO removeAiPlayer(Long roomId, Long playerId) {
        Long userId = UserContext.getUserId();
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        if (!room.getOwnerUserId().equals(userId)) {
            throw new BusinessException("只有房主可以移除 AI 玩家");
        }
        if (!RoomStatus.WAITING.name().equals(room.getStatus())) {
            throw new BusinessException("游戏已经开始了");
        }

        Player player = playerMapper.selectById(playerId);
        if (player == null || !player.getRoomId().equals(roomId)) {
            throw new BusinessException("该席位不在此房间中");
        }
        if (!player.isAiPlayer()) {
            throw new BusinessException("只能移除 AI 玩家，真人玩家请用「踢出」");
        }

        playerMapper.deleteById(player.getId());
        log.info("房主 {} 把 AI「{}」移出了房间 {}", userId, player.getAiName(), roomId);

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
        // AI 脑子：没设过（老房间）就当规则 AI；顺便告诉前端"大模型到底能不能用"
        vo.setAiBrain(room.getAiBrain() == null || room.getAiBrain().isBlank()
                ? "rule" : room.getAiBrain());
        vo.setLlmAvailable(aiBrainRouter.isLlmAvailable());

        // 一次性把用到的用户查出来，避免每个玩家查一次库（AI 玩家没有 userId，跳过）
        Map<Long, User> userMap = new HashMap<>();
        List<Long> userIds = new ArrayList<>();
        for (Player p : players) {
            if (p.getUserId() != null) {
                userIds.add(p.getUserId());
            }
        }
        if (!userIds.isEmpty()) {
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
            pv.setOwner(p.getUserId() != null && p.getUserId().equals(room.getOwnerUserId()));

            if (p.isAiPlayer()) {
                // AI：名字用自己的、永远在线、头像为空（前端显示默认空白头像）
                pv.setAi(true);
                pv.setAiName(p.getAiName());
                pv.setNickname(p.getAiName());
                pv.setOnline(true);
                AiPersona persona = AiPersona.byKey(p.getAiPersona());
                if (persona != null) {
                    pv.setAiPersonaTag(persona.getTag());
                    // 把这个也传出去：前端要拿它禁用下拉里"已经被占用的性格"
                    pv.setAiPersonaKey(persona.getKey());
                }
            } else {
                User u = userMap.get(p.getUserId());
                if (u != null) {
                    pv.setNickname(u.getNickname());
                    pv.setAvatar(u.getAvatar());
                }
                // 在线状态：WebSocket 还连着就算在线
                pv.setOnline(isOnline(p.getUserId()));
            }
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
