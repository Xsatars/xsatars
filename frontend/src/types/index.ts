/**
 * 与后端对应的类型定义。
 *
 * 后端返回结构改了这里也要跟着改（暂时手工同步，字段名一一对应）。
 */

/** 后端统一返回结构，对应 com.aitable.common.Result */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 健康检查，对应 HealthVO */
export interface HealthVO {
  status: string
  appName: string
  javaVersion: string
  mysqlConnected: boolean
  mysqlVersion?: string
  mysqlDatabase?: string
  mysqlError?: string
  redisConnected: boolean
  redisVersion?: string
  redisError?: string
  checkedAt: string
}

/** 用户，对应 UserVO */
export interface UserVO {
  id: number
  username: string
  nickname: string
  avatar?: string
  /** USER=普通玩家 ADMIN=管理员 */
  role?: string
}

/** 登录结果，对应 LoginVO */
export interface LoginVO {
  token: string
  user: UserVO
  /** 本次是不是新注册的账号 */
  newUser: boolean
}

/** 房间里的玩家，对应 PlayerVO */
export interface PlayerVO {
  playerId: number
  userId: number
  nickname: string
  avatar?: string
  seatNo: number
  ready: boolean
  owner: boolean
  /** 是否在线（掉线会显示"离线"） */
  online: boolean
}

/** 房间详情，对应 RoomVO */
export interface RoomVO {
  id: number
  roomNo: string
  name: string
  ownerUserId: number
  gameType: string
  boardCode: string
  maxPlayers: number
  playerCount: number
  status: 'WAITING' | 'PLAYING' | 'FINISHED'
  currentGameId?: number
  players: PlayerVO[]
  /** 是否满足开局条件（满员且未开始） */
  canStart: boolean
}

/** WebSocket 消息类型，与后端 WsMessageType 对应 */
export type WsMessageType =
  | 'JOIN_ROOM'
  | 'LEAVE_ROOM'
  | 'START_GAME'
  | 'SPEAK'
  | 'WEREWOLF_KILL'
  | 'SEER_CHECK'
  | 'WITCH_SAVE'
  | 'WITCH_POISON'
  | 'VOTE'
  | 'ROOM_UPDATE'
  | 'GAME_START'
  | 'ROLE_ASSIGN'
  | 'PHASE_CHANGE'
  | 'PLAYER_SPEAK'
  | 'NIGHT_RESULT'
  | 'PLAYER_DEAD'
  | 'VOTE_UPDATE'
  | 'VOTE_RESULT'
  | 'GAME_STATE_UPDATE'
  | 'GAME_END'
  | 'ERROR'
  /** 你的账号在另一个页面登录了，当前页面作废 */
  | 'SESSION_REPLACED'

/** WebSocket 消息统一格式，对应后端 WsMessage */
export interface WsMessage<T = unknown> {
  type: WsMessageType
  data: T
  message?: string
  timestamp?: number
}

// ============================================================
// 对局相关（对应后端 game/core 里的视图对象）
// ============================================================

/** 一个座位在公共视野里的样子（里面没有身份信息） */
export interface SeatView {
  seatNo: number
  userId: number
  nickname: string
  alive: boolean
  /** 是否正在发言 */
  speaking: boolean
  /** 当前得票数 */
  voteCount: number
  /** 本阶段是否已经行动/投票过 */
  acted: boolean
  /** 是否在线（掉线的玩家会被自动跳过） */
  online: boolean
  /** 头像地址；为空表示还没设置头像（界面上显示默认空白头像） */
  avatar?: string | null
}

/** 公共状态：房间里所有人都一样的那部分 */
export interface PublicGameState {
  gameId: number
  roomId: number
  phase: string
  phaseLabel: string
  round: number
  aliveCount: number
  seats: SeatView[]
  winnerCamp?: string
  recentMessages: string[]
  /** 当前这一步的截止时间（毫秒时间戳），0 表示不计时；前端用它显示倒计时 */
  deadline: number
}

/** 私人状态：只有我自己能看到的东西 */
export interface PlayerPrivateState {
  mySeat: number
  alive: boolean
  myRole: string
  myRoleLabel: string
  myCamp: string
  /** 狼队友座位（只有狼人有） */
  teammateSeats: number[]
  /** 我此刻能做的行动 */
  availableActions: string[]
  hint: string
  info: Record<string, unknown>
}

/** 下发给我的完整视图 = 公共 + 我的私人 */
export interface GameView {
  publicState: PublicGameState
  privateState: PlayerPrivateState
}

/** 开局收到的身份牌（私有消息 ROLE_ASSIGN） */
export interface RoleAssignData {
  seatNo: number
  role: string
  roleLabel: string
  camp: string
  teammateSeats?: number[]
}

/** 游戏结束（公开消息 GAME_END） */
export interface GameEndData {
  winnerCamp: string
  winnerLabel: string
  rounds: number
  players: Array<{
    seatNo: number
    nickname: string
    role: string
    roleLabel: string
    camp: string
    alive: boolean
  }>
}

// ============================================================
// 管理后台相关（对应后端 com.aitable.vo.Admin*）
// ============================================================

/** 通用分页返回，对应后端 PageResult */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

/** 数据概览 */
export interface AdminOverviewVO {
  userCount: number
  adminCount: number
  todayNewUsers: number
  roomCount: number
  waitingRoomCount: number
  playingRoomCount: number
  finishedRoomCount: number
  gameCount: number
  finishedGameCount: number
  onlineCount: number
  system: HealthVO
  generatedAt: string
}

/** 用户管理里的一行 */
export interface AdminUserVO {
  id: number
  username: string
  nickname: string
  avatar?: string
  role: string
  status: number
  createdAt: string
  updatedAt: string
}

/** 房间管理里的一行 */
export interface AdminRoomVO {
  id: number
  roomNo: string
  name: string
  ownerUserId: number
  ownerNickname?: string
  gameType: string
  boardCode: string
  maxPlayers: number
  playerCount: number
  status: string
  currentGameId?: number
  createdAt: string
}

/** 对局记录里的一行 */
export interface AdminGameVO {
  id: number
  roomId: number
  roomNo?: string
  gameType: string
  boardCode: string
  status: string
  totalRounds: number
  winnerCamp?: string
  playerCount: number
  startedAt: string
  endedAt?: string
}

/** 对局里某个玩家的结果 */
export interface AdminGamePlayerResult {
  userId: number
  nickname: string
  seatNo: number
  roleCode: string
  camp: string
  result: string
  survived: number
}

/** 对局详情 */
export interface AdminGameDetailVO {
  game: AdminGameVO
  players: AdminGamePlayerResult[]
}
