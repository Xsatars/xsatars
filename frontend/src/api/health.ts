import request from './request'
import type { HealthVO, Result } from '@/types'

/**
 * 后端健康检查相关接口。
 *
 * 这些接口目前只用来验证"前端能正常请求后端"，后续做登录、房间、游戏时
 * 会在 api/ 目录下按模块继续添加文件，例如 api/auth.ts、api/room.ts。
 */

/** 最简单的连通性测试 */
export function ping() {
  return request.get<Result<string>>('/ping').then((res) => res.data)
}

/** 查看后端自身、MySQL、Redis 的状态 */
export function getHealth() {
  return request.get<Result<HealthVO>>('/health').then((res) => res.data)
}
