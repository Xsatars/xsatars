package com.aitable.service;

import com.aitable.common.BusinessException;
import com.aitable.common.GameConstants;
import com.aitable.common.RedisKeys;
import com.aitable.common.UserContext;
import com.aitable.common.enums.RoomStatus;
import com.aitable.dto.CreateRoomDTO;
import com.aitable.dto.RegisterDTO;
import com.aitable.entity.Room;
import com.aitable.entity.User;
import com.aitable.mapper.RoomMapper;
import com.aitable.mapper.UserMapper;
import com.aitable.vo.LoginVO;
import com.aitable.vo.PlayerVO;
import com.aitable.vo.RoomVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 房间功能测试（Step 3）。
 *
 * <p>模拟 6 人局的完整凑人过程：建房 → 5 人加入 → 满员 → 拒绝第 7 人 →
 * 房主离开导致房主转移。</p>
 */
@SpringBootTest
@Transactional
class RoomServiceTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final List<String> issuedTokens = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        issuedTokens.forEach(t -> stringRedisTemplate.delete(RedisKeys.token(t)));
        issuedTokens.clear();
        UserContext.clear();
    }

    /** 造一个用户并切换"当前登录用户"为它 */
    private User newUser(String prefix) {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(prefix + "_" + System.nanoTime());
        dto.setPassword("123456");
        dto.setNickname(prefix);
        LoginVO vo = userService.register(dto);
        issuedTokens.add(vo.getToken());
        UserContext.setUserId(vo.getUser().getId());
        return userMapper.selectById(vo.getUser().getId());
    }

    private PlayerVO ownerSeat(RoomVO room) {
        return room.getPlayers().stream().filter(PlayerVO::isOwner).findFirst().orElseThrow();
    }

    @Test
    void createRoomPutsOwnerOnSeatOne() {
        User owner = newUser("room_owner");
        RoomVO room = roomService.createRoom(new CreateRoomDTO());

        assertTrue(room.getRoomNo().matches("\\d{6}"), "房间号应是 6 位数字，实际: " + room.getRoomNo());
        assertEquals(RoomStatus.WAITING.name(), room.getStatus());
        assertEquals(GameConstants.WEREWOLF_6_PLAYER_COUNT, room.getMaxPlayers());
        assertEquals(1, room.getPlayerCount());
        assertFalse(room.isCanStart(), "只有 1 个人时不能开局");
        assertEquals(1, ownerSeat(room).getSeatNo());
        assertEquals(owner.getId(), room.getOwnerUserId());
        assertTrue(room.getName().contains("狼人杀"), "默认房间名应含游戏名");
    }

    @Test
    void sixPlayersFillTheRoomAndSeventhIsRejected() {
        User owner = newUser("fill_owner");
        RoomVO room = roomService.createRoom(new CreateRoomDTO());

        List<User> others = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            User user = newUser("fill_p" + i);
            others.add(user);
            roomService.joinRoom(room.getRoomNo());
        }

        RoomVO full = roomService.getRoom(room.getRoomNo());
        assertEquals(6, full.getPlayerCount());
        assertFalse(full.isCanStart(), "人满了但还没准备，不能开局");

        // 除房主外每个人都点准备 → 这时才能开局
        for (User user : others) {
            UserContext.setUserId(user.getId());
            roomService.setReady(room.getRoomNo(), true);
        }
        UserContext.setUserId(owner.getId());
        assertTrue(roomService.getRoom(room.getRoomNo()).isCanStart(), "人齐且都准备好了才能开局");

        Set<Integer> seats = new HashSet<>();
        full.getPlayers().forEach(p -> seats.add(p.getSeatNo()));
        assertEquals(6, seats.size(), "6 个人的座位号不能重复");
        for (int seat = 1; seat <= 6; seat++) {
            assertTrue(seats.contains(seat), "座位 " + seat + " 应该有人");
        }

        newUser("fill_extra");
        BusinessException e = assertThrows(BusinessException.class,
                () -> roomService.joinRoom(room.getRoomNo()));
        assertTrue(e.getMessage().contains("已满"), "满了之后应提示房间已满，实际: " + e.getMessage());
    }

    @Test
    void joiningTwiceDoesNotTakeTwoSeats() {
        newUser("twice_owner");
        RoomVO room = roomService.createRoom(new CreateRoomDTO());

        User player = newUser("twice_player");
        RoomVO first = roomService.joinRoom(room.getRoomNo());
        assertEquals(2, first.getPlayerCount());

        // 再点一次"加入"（前端刷新后会这样）
        RoomVO again = roomService.joinRoom(room.getRoomNo());
        assertEquals(2, again.getPlayerCount(), "重复加入不应该多占座位");

        long mySeats = again.getPlayers().stream()
                .filter(p -> p.getUserId().equals(player.getId())).count();
        assertEquals(1, mySeats, "同一个用户只能有一个座位");
    }

    @Test
    void ownerLeavingTransfersOwnershipToSmallestSeat() {
        User owner = newUser("leave_owner");
        RoomVO room = roomService.createRoom(new CreateRoomDTO());

        User second = newUser("leave_p1");
        roomService.joinRoom(room.getRoomNo());
        newUser("leave_p2");
        roomService.joinRoom(room.getRoomNo());

        RoomVO before = roomService.getRoom(room.getRoomNo());
        assertEquals(3, before.getPlayerCount());

        // 房主离开
        UserContext.setUserId(owner.getId());
        Long ownerPlayerId = ownerSeat(before).getPlayerId();
        roomService.leaveRoom(before.getId(), ownerPlayerId);

        RoomVO after = roomService.getRoom(String.valueOf(before.getId()));
        assertEquals(2, after.getPlayerCount());
        assertNotEquals(owner.getId(), after.getOwnerUserId(), "房主应该转给别人");
        assertEquals(second.getId(), after.getOwnerUserId(), "应转给座位号最小的那个人（2 号位）");
        assertEquals(2, ownerSeat(after).getSeatNo());
    }

    @Test
    void emptyRoomIsMarkedFinished() {
        User owner = newUser("empty_owner");
        RoomVO room = roomService.createRoom(new CreateRoomDTO());

        UserContext.setUserId(owner.getId());
        roomService.leaveRoom(room.getId(), ownerSeat(room).getPlayerId());

        RoomVO after = roomService.getRoom(String.valueOf(room.getId()));
        assertEquals(RoomStatus.FINISHED.name(), after.getStatus(), "房间没人了应标记为已结束");
        assertEquals(0, after.getPlayerCount());
    }

    @Test
    void cannotLeaveSomebodyElsesSeat() {
        newUser("steal_owner");
        RoomVO room = roomService.createRoom(new CreateRoomDTO());

        User other = newUser("steal_other");
        roomService.joinRoom(room.getRoomNo());

        // 想踢房主？不行
        UserContext.setUserId(other.getId());
        Long ownerPlayerId = ownerSeat(roomService.getRoom(room.getRoomNo())).getPlayerId();
        BusinessException e = assertThrows(BusinessException.class,
                () -> roomService.leaveRoom(room.getId(), ownerPlayerId));
        assertEquals("只能让自己离开房间", e.getMessage());
    }

    @Test
    void unknownRoomIsReported() {
        newUser("unknown_user");
        BusinessException e = assertThrows(BusinessException.class, () -> roomService.getRoom("999999"));
        assertEquals("房间不存在", e.getMessage());
    }

    // ============================================================
    // 准备功能
    // ============================================================

    private PlayerVO playerOf(RoomVO room, Long userId) {
        return room.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst().orElseThrow();
    }

    @Test
    void nonOwnerCanReadyAndCancel() {
        newUser("ready_owner");
        RoomVO room = roomService.createRoom(new CreateRoomDTO());

        User player = newUser("ready_p1");
        RoomVO joined = roomService.joinRoom(room.getRoomNo());
        assertFalse(playerOf(joined, player.getId()).isReady(), "刚加入时应该是未准备");

        RoomVO afterReady = roomService.setReady(room.getRoomNo(), true);
        assertTrue(playerOf(afterReady, player.getId()).isReady(), "点了准备之后应该是已准备");

        RoomVO afterCancel = roomService.setReady(room.getRoomNo(), false);
        assertFalse(playerOf(afterCancel, player.getId()).isReady(), "取消准备之后应该变回未准备");
    }

    @Test
    void ownerIsReadyByDefaultAndCannotToggle() {
        User owner = newUser("owner_ready");
        RoomVO room = roomService.createRoom(new CreateRoomDTO());

        PlayerVO ownerSeat = room.getPlayers().get(0);
        assertTrue(ownerSeat.isOwner());
        assertTrue(ownerSeat.isReady(), "房主创建房间时就是已就绪（他不需要准备）");

        BusinessException e = assertThrows(BusinessException.class,
                () -> roomService.setReady(room.getRoomNo(), false));
        assertTrue(e.getMessage().contains("房主不需要准备"), e.getMessage());
    }

    @Test
    void cannotReadyAfterGameStarted() {
        User owner = newUser("started_owner");
        RoomVO room = roomService.createRoom(new CreateRoomDTO());

        User player = newUser("started_p1");
        roomService.joinRoom(room.getRoomNo());
        roomService.setReady(room.getRoomNo(), true);

        // 把房间状态直接改成"对局中"，模拟已经开局
        Room entity = roomMapper.selectById(room.getId());
        entity.setStatus(RoomStatus.PLAYING.name());
        roomMapper.updateById(entity);

        UserContext.setUserId(player.getId());
        BusinessException e = assertThrows(BusinessException.class,
                () -> roomService.setReady(room.getRoomNo(), false));
        assertTrue(e.getMessage().contains("已经开始"), e.getMessage());

        UserContext.setUserId(owner.getId());
    }

    @Test
    void roomCanBeFoundByRoomNoAndById() {
        newUser("find_owner");
        RoomVO created = roomService.createRoom(new CreateRoomDTO());

        RoomVO byNo = roomService.getRoom(created.getRoomNo());
        RoomVO byId = roomService.getRoom(String.valueOf(created.getId()));

        assertEquals(created.getId(), byNo.getId());
        assertEquals(created.getId(), byId.getId());
        assertEquals(created.getRoomNo(), byId.getRoomNo());
    }
}
