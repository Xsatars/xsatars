package com.aitable.controller;

import com.aitable.common.AvatarStorage;
import com.aitable.common.Result;
import com.aitable.common.UserContext;
import com.aitable.dto.LoginDTO;
import com.aitable.dto.RegisterDTO;
import com.aitable.dto.ResetPasswordDTO;
import com.aitable.dto.UpdateNicknameDTO;
import com.aitable.dto.UpdatePasswordDTO;
import com.aitable.service.UserService;
import com.aitable.vo.LoginVO;
import com.aitable.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户接口。
 *
 * <p>Controller 只负责接参数、调 Service、返回结果，没有任何业务逻辑。</p>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AvatarStorage avatarStorage;

    public UserController(UserService userService, AvatarStorage avatarStorage) {
        this.userService = userService;
        this.avatarStorage = avatarStorage;
    }

    /**
     * 注册新账号。成功后直接返回 token，前端不用再让用户登录一次。
     */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success("注册成功", userService.register(dto));
    }

    /**
     * 登录。账号不存在会提示去注册（不会自动建账号）。
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success("登录成功", userService.login(dto));
    }

    /**
     * 查询当前登录用户。前端刷新页面后用它恢复登录状态。
     */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(UserVO.from(userService.getById(UserContext.getUserId())));
    }

    /**
     * 忘记密码：用「用户名 + 昵称」验证身份后重设密码。
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(dto.getUsername(), dto.getNickname(), dto.getNewPassword());
        return Result.success("密码已重置，请用新密码登录", null);
    }

    /** 修改昵称 */
    @PutMapping("/me/nickname")
    public Result<UserVO> updateNickname(@Valid @RequestBody UpdateNicknameDTO dto) {
        UserVO vo = userService.updateNickname(UserContext.getUserId(), dto.getNickname());
        return Result.success("昵称已更新", vo);
    }

    /** 修改密码（要填原密码） */
    @PutMapping("/me/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordDTO dto) {
        userService.updatePassword(UserContext.getUserId(), dto.getOldPassword(), dto.getNewPassword());
        return Result.success("密码已修改", null);
    }

    /** 上传头像 */
    @PostMapping("/me/avatar")
    public Result<UserVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getUserId();
        String oldAvatar = userService.getById(userId).getAvatar();

        String url = avatarStorage.store(userId, file);
        UserVO vo = userService.updateAvatar(userId, url);
        // 换新头像后把旧文件删掉，免得磁盘上堆积一堆没人用的图片
        avatarStorage.deleteQuietly(oldAvatar);
        return Result.success("头像已更新", vo);
    }

    /** 清除头像（界面上会显示成默认的空白头像） */
    @DeleteMapping("/me/avatar")
    public Result<UserVO> clearAvatar() {
        Long userId = UserContext.getUserId();
        String oldAvatar = userService.getById(userId).getAvatar();

        UserVO vo = userService.updateAvatar(userId, null);
        avatarStorage.deleteQuietly(oldAvatar);
        return Result.success("头像已清除", vo);
    }
}
