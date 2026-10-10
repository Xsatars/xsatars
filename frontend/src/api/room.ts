import request from './request'
import type { AiPersonaVO, BoardVO, Result, RoomVO } from '@/types'

/**
 * 房间相关接口。
 *
 * 路径里的 roomId 既可以传 6 位房间号，也可以传房间ID —— 前端用房间号就行。
 */

/** 创建房间（创建者自动成为房主并坐 1 号位） */
export function createRoom(name?: string, boardCode?: string) {
  return request.post<Result<RoomVO>>('/rooms', { name, boardCode }).then((res) => res.data)
}

/** 有哪些板子可选（6 人局 / 8 人局）—— 前端不硬编码，加板子只改后端 */
export function getBoards() {
  return request.get<Result<BoardVO[]>>('/rooms/boards').then((res) => res.data)
}

/**
 * 切换 AI 的脑子：{@code rule}（规则 AI）或 {@code llm}（大模型 AI）。
 *
 * <p>下一局生效 —— 不让它在对局中途换，否则同一个 AI 前后行为不一致，
 * 玩家会觉得它精神分裂。</p>
 */
export function updateAiBrain(roomId: number | string, aiBrain: string) {
  return request
    .put<Result<RoomVO>>(`/rooms/${roomId}/ai-brain`, { aiBrain })
    .then((res) => res.data)
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

/** 房主添加一个 AI 玩家补位（一个人也能开局）；可以指定性格，不指定就自动挑一个 */
export function addAiPlayer(roomId: number, persona?: string) {
  return request
    .post<Result<RoomVO>>(`/rooms/${roomId}/ai`, null, { params: { persona } })
    .then((res) => res.data)
}

/** 所有可选的 AI 性格（给下拉框用，前端不硬编码这份列表） */
export function getAiPersonas() {
  return request.get<Result<AiPersonaVO[]>>('/rooms/ai/personas').then((res) => res.data)
}

/** 房主移除一个 AI 玩家（只能移除 AI，真人要用踢人） */
export function removeAiPlayer(roomId: number, playerId: number) {
  return request.delete<Result<RoomVO>>(`/rooms/${roomId}/ai/${playerId}`).then((res) => res.data)
}
