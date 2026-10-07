package com.aitable.mapper;

import com.aitable.common.enums.Camp;
import com.aitable.common.enums.GameStatus;
import com.aitable.common.enums.Role;
import com.aitable.common.enums.RoomStatus;
import com.aitable.entity.Game;
import com.aitable.entity.GameRecord;
import com.aitable.entity.Player;
import com.aitable.entity.Room;
import com.aitable.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 五张表的读写冒烟测试（Step 2 的验证）。
 *
 * <p>这个测试连接真实的 MySQL（ai_table 库），但加了 {@code @Transactional}，
 * 方法结束后会自动回滚，不会往库里留下垃圾数据。</p>
 */
@SpringBootTest
@Transactional
class MapperSmokeTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private GameMapper gameMapper;

    @Autowired
    private GameRecordMapper gameRecordMapper;

    @Test
    void allFiveTablesCanInsertAndSelect() {
        long stamp = System.currentTimeMillis();

        // ---------- users ----------
        User user = new User();
        user.setUsername("smoke_" + stamp);
        user.setPassword("dummy-hash");
        user.setNickname("冒烟测试玩家");
        user.setStatus(1);
        assertEquals(1, userMapper.insert(user));
        assertNotNull(user.getId(), "插入后应回填自增主键");
        assertNotNull(user.getCreatedAt(), "createdAt 应由 MyBatis-Plus 自动填充");

        // ---------- rooms ----------
        Room room = new Room();
        room.setRoomNo(String.valueOf(stamp % 1000000));
        room.setName("冒烟测试房间");
        room.setOwnerUserId(user.getId());
        room.setGameType("WEREWOLF");
        room.setBoardCode("WEREWOLF_6");
        room.setMaxPlayers(6);
        room.setStatus(RoomStatus.WAITING.name());
        assertEquals(1, roomMapper.insert(room));

        // ---------- players ----------
        Player player = new Player();
        player.setRoomId(room.getId());
        player.setUserId(user.getId());
        player.setSeatNo(1);
        player.setReady(0);
        assertEquals(1, playerMapper.insert(player));

        // ---------- games ----------
        Game game = new Game();
        game.setRoomId(room.getId());
        game.setGameType("WEREWOLF");
        game.setBoardCode("WEREWOLF_6");
        game.setStatus(GameStatus.PLAYING.name());
        game.setTotalRounds(0);
        assertEquals(1, gameMapper.insert(game));

        // ---------- game_records ----------
        GameRecord record = new GameRecord();
        record.setGameId(game.getId());
        record.setUserId(user.getId());
        record.setSeatNo(1);
        record.setRoleCode(Role.SEER.name());
        record.setCamp(Role.SEER.getCamp().name());
        record.setResult("WIN");
        record.setSurvived(1);
        assertEquals(1, gameRecordMapper.insert(record));

        // ---------- 查回来核对 ----------
        User loaded = userMapper.selectById(user.getId());
        assertNotNull(loaded);
        assertEquals(user.getUsername(), loaded.getUsername());
        assertEquals("冒烟测试玩家", loaded.getNickname(), "中文应能正确存取（utf8mb4）");

        Room loadedRoom = roomMapper.selectById(room.getId());
        assertEquals(RoomStatus.WAITING.name(), loadedRoom.getStatus());
        assertEquals(6, loadedRoom.getMaxPlayers());

        Game loadedGame = gameMapper.selectById(game.getId());
        assertEquals(GameStatus.PLAYING.name(), loadedGame.getStatus());

        GameRecord loadedRecord = gameRecordMapper.selectById(record.getId());
        assertEquals(Role.SEER.name(), loadedRecord.getRoleCode());
        assertEquals(Camp.GOOD.name(), loadedRecord.getCamp());

        // ---------- 自动填充：更新时应刷新 updatedAt ----------
        loadedRoom.setName("改过名字的房间");
        roomMapper.updateById(loadedRoom);
        assertNotNull(roomMapper.selectById(room.getId()).getUpdatedAt());
    }

    @Test
    void seatNumberIsUniqueInsideOneRoom() {
        long stamp = System.currentTimeMillis();

        Room room = new Room();
        room.setRoomNo(String.valueOf((stamp + 7) % 1000000));
        room.setOwnerUserId(1L);
        room.setGameType("WEREWOLF");
        room.setBoardCode("WEREWOLF_6");
        room.setMaxPlayers(6);
        room.setStatus(RoomStatus.WAITING.name());
        roomMapper.insert(room);

        Player first = new Player();
        first.setRoomId(room.getId());
        first.setUserId(1001L);
        first.setSeatNo(3);
        first.setReady(0);
        playerMapper.insert(first);

        // 同一个房间的同一个座位号不能再坐第二个人
        Player second = new Player();
        second.setRoomId(room.getId());
        second.setUserId(1002L);
        second.setSeatNo(3);
        second.setReady(0);
        Exception e = assertThrows(Exception.class, () -> playerMapper.insert(second));
        assertTrue(e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate"),
                "应该是唯一索引冲突，实际异常: " + e.getMessage());
    }
}
