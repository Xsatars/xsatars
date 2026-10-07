package com.aitable.service;

import com.aitable.dto.LoginDTO;
import com.aitable.dto.RegisterDTO;
import com.aitable.entity.User;
import com.aitable.vo.LoginVO;
import com.aitable.vo.UserVO;

/**
 * 用户相关业务。
 */
public interface UserService {

    /** 注册新账号，成功后直接返回 token（省得再登录一次） */
    LoginVO register(RegisterDTO dto);

    /**
     * 登录。
     *
     * <p>账号不存在时<b>不会</b>自动注册，而是提示去注册——
     * 否则用户名打错一个字就会悄悄多出一个账号。</p>
     */
    LoginVO login(LoginDTO dto);

    /** 按ID查用户，查不到抛业务异常 */
    User getById(Long userId);

    /** 修改昵称 */
    UserVO updateNickname(Long userId, String nickname);

    /** 修改密码（要先验证原密码） */
    void updatePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 忘记密码：用「用户名 + 昵称」验证身份后直接设新密码。
     *
     * <p>说明：这是没有邮箱 / 短信服务时的简化做法，安全性不如验证码，
     * 正式上线前应该换成真实渠道验证。</p>
     */
    void resetPassword(String username, String nickname, String newPassword);

    /**
     * 更新头像。
     *
     * @param avatarUrl 头像地址；传 null 或空串表示清空，界面上会显示成默认的空白头像
     */
    UserVO updateAvatar(Long userId, String avatarUrl);
}
