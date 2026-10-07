import request from './request'
import type { LoginVO, Result, UserVO } from '@/types'

/**
 * 注册新账号。成功后后端直接返回 token，前端不用再让用户登录一次。
 */
export function register(data: { username: string; password: string; nickname: string }) {
  return request.post<Result<LoginVO>>('/users/register', data).then((res) => res.data)
}

/**
 * 登录。账号不存在时后端会提示"账号不存在，请先注册"。
 */
export function login(data: { username: string; password: string }) {
  return request.post<Result<LoginVO>>('/users/login', data).then((res) => res.data)
}

/** 查询当前登录用户（刷新页面后用它恢复登录状态） */
export function me() {
  return request.get<Result<UserVO>>('/users/me').then((res) => res.data)
}
