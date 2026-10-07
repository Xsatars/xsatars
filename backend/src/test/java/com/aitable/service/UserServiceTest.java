package com.aitable.service;

import com.aitable.common.BusinessException;
import com.aitable.common.RedisKeys;
import com.aitable.dto.LoginDTO;
import com.aitable.dto.RegisterDTO;
import com.aitable.entity.User;
import com.aitable.mapper.UserMapper;
import com.aitable.vo.LoginVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册与登录功能测试。
 *
 * <p>MySQL 部分靠 {@code @Transactional} 回滚；
 * Redis 里的 token 不在事务里，所以在 {@code @AfterEach} 里手动删掉。</p>
 */
@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 测试过程中产生的 token，结束后清理 */
    private final List<String> issuedTokens = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        issuedTokens.forEach(t -> stringRedisTemplate.delete(RedisKeys.token(t)));
        issuedTokens.clear();
    }

    private LoginVO register(String username, String password, String nickname) {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setNickname(nickname);
        LoginVO vo = userService.register(dto);
        issuedTokens.add(vo.getToken());
        return vo;
    }

    private LoginVO login(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        LoginVO vo = userService.login(dto);
        issuedTokens.add(vo.getToken());
        return vo;
    }

    @Test
    void registerCreatesAccountAndIssuesToken() {
        String username = "reg_" + System.nanoTime();

        LoginVO vo = register(username, "123456", "张三");

        assertTrue(vo.isNewUser(), "注册返回的 newUser 应该是 true");
        assertNotNull(vo.getToken());
        assertEquals(username, vo.getUser().getUsername());
        assertEquals("张三", vo.getUser().getNickname());

        // token 真的写进了 Redis
        String cached = stringRedisTemplate.opsForValue().get(RedisKeys.token(vo.getToken()));
        assertEquals(String.valueOf(vo.getUser().getId()), cached);

        // 库里只有一条记录
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        assertEquals(1L, count);
    }

    @Test
    void duplicateUsernameIsRejected() {
        String username = "dup_" + System.nanoTime();
        register(username, "123456", "第一个人");

        BusinessException e = assertThrows(BusinessException.class,
                () -> register(username, "654321", "第二个人"));
        assertEquals("该用户名已被注册", e.getMessage());
    }

    @Test
    void loginWithUnknownAccountTellsUserToRegister() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> login("not_exist_" + System.nanoTime(), "123456"));
        assertEquals("账号不存在，请先注册", e.getMessage());
    }

    @Test
    void loginAfterRegisterWorks() {
        String username = "flow_" + System.nanoTime();
        LoginVO registered = register(username, "123456", "流程测试");

        LoginVO logged = login(username, "123456");
        assertFalse(logged.isNewUser(), "登录不应该再标记为新用户");
        assertEquals(registered.getUser().getId(), logged.getUser().getId(), "应该是同一个用户");
        assertNotEquals(registered.getToken(), logged.getToken(), "每次登录发新 token");
    }

    @Test
    void passwordIsStoredEncrypted() {
        String username = "pwd_" + System.nanoTime();
        LoginVO vo = register(username, "my-secret-123", "密码测试");

        User saved = userMapper.selectById(vo.getUser().getId());
        assertNotEquals("my-secret-123", saved.getPassword(), "密码不能明文存库");
        assertTrue(saved.getPassword().startsWith("$2"), "应该是 BCrypt 哈希，实际: " + saved.getPassword());
    }

    @Test
    void wrongPasswordIsRejected() {
        String username = "wrong_" + System.nanoTime();
        register(username, "correct-password", "密码错误测试");

        BusinessException e = assertThrows(BusinessException.class, () -> login(username, "bad-password"));
        assertEquals("用户名或密码错误", e.getMessage());
    }

    @Test
    void disabledAccountCannotLogin() {
        String username = "disabled_" + System.nanoTime();
        LoginVO vo = register(username, "123456", "被禁用的用户");

        User user = userMapper.selectById(vo.getUser().getId());
        user.setStatus(0);
        userMapper.updateById(user);

        BusinessException e = assertThrows(BusinessException.class, () -> login(username, "123456"));
        assertEquals("该账号已被禁用", e.getMessage());
    }
}
