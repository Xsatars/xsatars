import request from './request'
import type { GameView, Result } from '@/types'

/**
 * 对局相关接口。
 *
 * 游戏过程中前端主要靠 WebSocket 收推送，这个接口只在
 * 进入页面 / 刷新 / 断线重连时用来把最新状态拉回来。
 */

/** 查询我在这局里的视图（公共状态 + 我的私人信息与可用动作） */
export function getGame(gameId: number) {
  return request.get<Result<GameView>>(`/games/${gameId}`).then((res) => res.data)
}

/**
 * 查我当前有没有还没结束的对局（没有就是 null）。
 *
 * 中途退出（关页面、断网）后再回来，靠它把人领回原来的对局。
 */
export function getCurrentGame() {
  return request.get<Result<GameView | null>>('/games/current').then((res) => res.data)
}
