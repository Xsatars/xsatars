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
/** 一个可选的 AI 性格（后端下发，供下拉框使用） */
export interface AiPersonaVO {
  /** 提交时回传的标识，如 HOTHEAD */
  key: string
  /** 名字，如"阿豆" */
  name: string
  /** 性格标签，如"急脾气" */
  tag: string
  /** 口吻描述，可以当提示文字 */
  style: string
}

export interface PlayerVO {
  playerId: number
  /** AI 玩家没有真实账号，这里是 null */
  userId: number | null
  nickname: string
  avatar?: string
  seatNo: number
  ready: boolean
  owner: boolean
  /** 是否在线（掉线会显示"离线"；AI 永远在线） */
  online: boolean
  /** 是不是 AI 玩家 */
  ai: boolean
  /** AI 的名字（只有 AI 有） */
  aiName?: string
  /** AI 的性格标签（如"急脾气"） */
  aiPersonaTag?: string
  /** AI 的性格标识（如 HOTHEAD）；前端用它禁用"已被占用的性格" */
  aiPersonaKey?: string
}

/** 房间详情，对应 RoomVO */export interface RoomVO {
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
  /** AI 用哪个脑子：rule（规则）/ llm（大模型）。房主可切换，下一局生效 */
  aiBrain?: string
  /** 服务端到底能不能用大模型（没配 API Key 就是 false） */
  llmAvailable?: boolean
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
  /** 主持人的解说（天亮 / 放逐 / 游戏结束） */
  | 'HOST_SAY'
  /** AI 的局后复盘 */
  | 'AI_REFLECTION'
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
  /** 是不是 AI 玩家 */
  ai?: boolean
  /** AI 的性格标签（如"急脾气"），显示在名字旁边 */
  aiPersonaTag?: string
}

/** 公共状态：房间里所有人都一样的那部分 */
export interface PublicGameState {
  gameId: number
  roomId: number
  phase: string
  phaseLabel: string
  /**
   * 状态版本号（单调递增）。
   *
   * 前端同时从两个 STOMP 频道收状态（房间频道的事件 + 用户频道的全量状态），
   * 而 STOMP **不保证跨频道的到达顺序**。收到快照时要比较这个号，
   * 只接受"不比当前更旧"的，否则旧快照会覆盖新状态 ——
   * 表现就是"明明是白天，闪一下狼人正在刀人"。
   */
  stateSeq?: number
  round: number
  aliveCount: number
  seats: SeatView[]
  winnerCamp?: string
  recentMessages: string[]
  /** 发言历史（跨轮保留，整局都留着；前端拿来做对局回顾） */
  speechHistory?: string[]
  /** 投票历史（跨轮保留） */
  voteHistory?: string[]
  /**
   * 夜晚现在轮到哪个角色行动：WOLF / WITCH / SEER；不在夜晚时为 null。
   * 这是公开信息（跟主持人喊"狼人请睁眼"一样），不包含任何目标。
   */
  nightActor?: string | null
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

/** 一个可选的板子（6 人局 / 8 人局） */
export interface BoardVO {
  code: string
  label: string
  /** 角色构成说明，如"2 狼 · 预言家 · 女巫 · 猎人 · 3 平民" */
  composition: string
  playerCount: number
  wolfCount: number
}

/** 开局收到的身份牌（私有消息 ROLE_ASSIGN） */
export interface RoleAssignData {
  seatNo: number
  role: string
  roleLabel: string
  camp: string
  teammateSeats?: number[]
}

/** 投票结果（公开消息 VOTE_RESULT） */
export interface VoteResultData {
  /** 各座位得票数 */
  voteCounts?: Record<string, number>
  /** 得票最高的人（平票时会有多个） */
  topSeats?: number[]
  /** 是否平票 */
  tie?: boolean
  /** 被放逐的人；平票或没人投票时为 null */
  exiledSeat?: number | null
  exiledNickname?: string
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
