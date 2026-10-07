package com.aitable.service.impl;

import com.aitable.common.BusinessException;
import com.aitable.common.RedisKeys;
import com.aitable.common.enums.UserRole;
import com.aitable.dto.LoginDTO;
import com.aitable.dto.RegisterDTO;
import com.aitable.entity.User;
import com.aitable.mapper.UserMapper;
import com.aitable.service.UserService;
import com.aitable.vo.LoginVO;
import com.aitable.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

/**
 * 用户业务实现。
 *
 * <p>登录逻辑：用户名不存在就自动注册；存在就校验密码。
 * 登录成功后发一个随机 token 存进 Redis（24 小时有效），
 * 后续请求带这个 token 就能认出是谁。</p>
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /** 登录有效期 */
    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordEncoder passwordEncoder;

    /** 管理员用户名名单，来自 application.yml */
    @Value("${ai-table.admin-usernames:admin}")
    private String adminUsernames;

    public UserServiceImpl(UserMapper userMapper,
                           StringRedisTemplate stringRedisTemplate,
                           PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        String username = dto.getUsername().trim();

        User user = findByUsername(username);
        if (user == null) {
            // 不再自动注册：用户名打错一个字就悄悄建个新账号，用户会一头雾水
            throw new BusinessException("账号不存在，请先注册");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("该账号已被禁用");
        }
        // 配置里把某人设成管理员后，他下次登录就自动获得管理员角色，不用手动改数据库
        upgradeToAdminIfConfigured(user);

        return buildLoginVO(user, false);
    }

    @Override
    public LoginVO register(RegisterDTO dto) {
        String username = dto.getUsername().trim();
        if (findByUsername(username) != null) {
            throw new BusinessException("该用户名已被注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname().trim());
        user.setStatus(1);
        user.setRole(isConfiguredAdmin(username) ? UserRole.ADMIN.name() : UserRole.USER.name());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 两个人同时注册同一个用户名时，唯一索引会拦住其中一个
            throw new BusinessException("该用户名已被注册");
        }
        log.info("新用户注册成功: id={}, username={}", user.getId(), username);

        // 注册完直接给 token，前端不用再让用户登一次
        return buildLoginVO(user, true);
    }

    private LoginVO buildLoginVO(User user, boolean newUser) {
        LoginVO vo = new LoginVO();
        vo.setToken(issueToken(user.getId()));
        vo.setUser(UserVO.from(user));
        vo.setNewUser(newUser);
        return vo;
    }

    /** 用户名在管理员名单里，就确保它是 ADMIN 角色 */
    private void upgradeToAdminIfConfigured(User user) {
        if (isConfiguredAdmin(user.getUsername()) && !UserRole.isAdmin(user.getRole())) {
            user.setRole(UserRole.ADMIN.name());
            userMapper.updateById(user);
            log.info("用户 {} 已按配置提升为管理员", user.getUsername());
        }
    }

    /** 用户名是否在配置的管理员名单里（application.yml: ai-table.admin-usernames） */
    private boolean isConfiguredAdmin(String username) {
        if (adminUsernames == null || adminUsernames.isBlank()) {
            return false;
        }
        for (String name : adminUsernames.split(",")) {
            if (name.trim().equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public User getById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    @Override
    public UserVO updateNickname(Long userId, String nickname) {
        User user = getById(userId);
        user.setNickname(nickname.trim());
        userMapper.updateById(user);
        log.info("用户 {} 把昵称改成了 {}", userId, user.getNickname());
        return UserVO.from(user);
    }

    @Override
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = getById(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException("新密码不能和原密码一样");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        log.info("用户 {} 修改了密码", userId);
    }

    @Override
    public void resetPassword(String username, String nickname, String newPassword) {
        User user = findByUsername(username == null ? "" : username.trim());
        String inputNickname = nickname == null ? "" : nickname.trim();

        // 账号不存在和昵称对不上，都用同一句提示，避免被人拿来试探哪些账号存在
        if (user == null || user.getNickname() == null || !user.getNickname().equals(inputNickname)) {
            throw new BusinessException("用户名和昵称对不上，无法重置密码");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        log.info("用户 {} 通过「忘记密码」重置了密码", user.getId());
    }

    @Override
    public UserVO updateAvatar(Long userId, String avatarUrl) {
        User user = getById(userId);
        // 传空就清掉，界面上会显示成默认的空白头像
        user.setAvatar(StringUtils.hasText(avatarUrl) ? avatarUrl.trim() : null);
        userMapper.updateById(user);
        log.info("用户 {} 更新了头像: {}", userId, user.getAvatar());
        return UserVO.from(user);
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    private String issueToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(RedisKeys.token(token), String.valueOf(userId), TOKEN_TTL);
        return token;
    }
}
