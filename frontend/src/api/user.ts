import request from './request'
import type { Result, UserVO } from '@/types'

/**
 * 个人资料相关接口（都要登录）。
 */

/** 修改昵称 */
export function updateNickname(nickname: string) {
  return request.put<Result<UserVO>>('/users/me/nickname', { nickname }).then((res) => res.data)
}

/** 修改密码（要填原密码） */
export function updatePassword(oldPassword: string, newPassword: string) {
  return request
    .put<Result<void>>('/users/me/password', { oldPassword, newPassword })
    .then((res) => res.data)
}

/**
 * 忘记密码：用「用户名 + 昵称」验证身份后重设密码。
 *
 * 这个接口不需要登录（都忘了密码了）。
 */
export function resetPassword(username: string, nickname: string, newPassword: string) {
  return request
    .post<Result<void>>('/users/reset-password', { username, nickname, newPassword })
    .then((res) => res.data)
}

/** 上传头像（最大 2MB，支持 png/jpg/gif/webp） */
export function uploadAvatar(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request
    .post<Result<UserVO>>('/users/me/avatar', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    .then((res) => res.data)
}

/** 清除头像，恢复成默认的空白头像 */
export function clearAvatar() {
  return request.delete<Result<UserVO>>('/users/me/avatar').then((res) => res.data)
}
