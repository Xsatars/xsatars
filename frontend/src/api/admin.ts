import request from './request'
import type {
  AdminGameDetailVO,
  AdminGameVO,
  AdminOverviewVO,
  AdminRoomVO,
  AdminUserVO,
  PageResult,
  Result
} from '@/types'

/**
 * 管理后台接口。
 *
 * 后端 /api/admin/** 只允许 ADMIN 角色的用户访问，普通用户会收到 403。
 */

/** ① 数据概览 */
export function getOverview() {
  return request.get<Result<AdminOverviewVO>>('/admin/overview').then((res) => res.data)
}

/** ② 用户列表 */
export function listUsers(params: { page: number; size: number; keyword?: string; status?: number }) {
  return request
    .get<Result<PageResult<AdminUserVO>>>('/admin/users', { params })
    .then((res) => res.data)
}

/** ② 启用 / 禁用用户 */
export function updateUserStatus(userId: number, status: number) {
  return request
    .put<Result<null>>(`/admin/users/${userId}/status`, { status })
    .then((res) => res.data)
}

/** ③ 房间列表 */
export function listRooms(params: { page: number; size: number; status?: string }) {
  return request
    .get<Result<PageResult<AdminRoomVO>>>('/admin/rooms', { params })
    .then((res) => res.data)
}

/** ③ 强制关闭房间 */
export function closeRoom(roomId: number) {
  return request.post<Result<null>>(`/admin/rooms/${roomId}/close`).then((res) => res.data)
}

/** ④ 对局记录列表 */
export function listGames(params: { page: number; size: number }) {
  return request
    .get<Result<PageResult<AdminGameVO>>>('/admin/games', { params })
    .then((res) => res.data)
}

/** ④ 对局详情 */
export function getGameDetail(gameId: number) {
  return request.get<Result<AdminGameDetailVO>>(`/admin/games/${gameId}`).then((res) => res.data)
}
