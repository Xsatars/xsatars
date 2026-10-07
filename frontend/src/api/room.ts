import request from './request'
import type { Result, RoomVO } from '@/types'

/**
 * 房间相关接口。
 *
 * 路径里的 roomId 既可以传 6 位房间号，也可以传房间ID —— 前端用房间号就行。
 */

/** 创建房间（创建者自动成为房主并坐 1 号位） */
export function createRoom(name?: string) {
  return request.post<Result<RoomVO>>('/rooms', { name }).then((res) => res.data)
}

/** 加入房间 */
export function joinRoom(roomIdOrNo: string | number) {
  return request.post<Result<RoomVO>>(`/rooms/${roomIdOrNo}/join`).then((res) => res.data)
}

/** 查询房间详情 */
export function getRoom(roomIdOrNo: string | number) {
  return request.get<Result<RoomVO>>(`/rooms/${roomIdOrNo}`).then((res) => res.data)
}

/** 离开房间（只能让自己离开） */
export function leaveRoom(roomId: number, playerId: number) {
  return request.delete<Result<null>>(`/rooms/${roomId}/players/${playerId}`).then((res) => res.data)
}

/** 房主开始游戏（满员才能开局），返回新对局ID */
export function startGame(roomId: number) {
  return request.post<Result<number>>(`/rooms/${roomId}/start`).then((res) => res.data)
}

/** 准备 / 取消准备（房主不需要准备，调了会被后端拒绝） */
export function setReady(roomId: number, ready: boolean) {
  return request.post<Result<RoomVO>>(`/rooms/${roomId}/ready`, { ready }).then((res) => res.data)
}

/** 房主把某个成员踢出房间（只有房主能用，而且只能踢别人） */
export function kickPlayer(roomId: number, playerId: number) {
  return request
    .post<Result<RoomVO>>(`/rooms/${roomId}/players/${playerId}/kick`)
    .then((res) => res.data)
}
