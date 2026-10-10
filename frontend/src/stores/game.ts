import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { getGame } from '@/api/game'
import { useAuthStore } from './auth'
import { wsClient } from '@/ws/client'
import type {
  GameEndData,
  GameView,
  PlayerPrivateState,
  PublicGameState,
  RoleAssignData,
  VoteResultData,
  WsMessage
} from '@/types'

/** 一条游戏公告（右侧滚动显示） */
export interface GameLog {
  time: string
  text: string
  kind: 'system' | 'phase' | 'death' | 'speak' | 'vote' | 'host'
}

/**
 * 对局状态。
 *
 * 数据全部来自服务器：
 *   - 公共状态（谁活着、几票、什么阶段）→ 房间频道广播
 *   - 我的私人状态（我的身份、我能做什么）→ 我的私人频道
 *
 * 前端**不做任何规则判断**：能点哪些按钮完全看服务器下发的 availableActions。
 */
export const useGameStore = defineStore('game', () => {
  const gameId = ref<number | null>(null)
  const roomId = ref<number | null>(null)
  const publicState = ref<PublicGameState | null>(null)
  const privateState = ref<PlayerPrivateState | null>(null)
  const roleCard = ref<RoleAssignData | null>(null)
  const logs = ref<GameLog[]>([])
  const endResult = ref<GameEndData | null>(null)
  /** 主持人最近说的一句话（在界面上单独醒目显示） */
  const hostLine = ref<{ text: string; moment: string } | null>(null)
  /** AI 的局后复盘：座位号 → 那句话 */
  const reflections = ref<Record<string, string>>({})
  /** 最近一次投票结果（用来弹"谁出局了"） */
  const voteResult = ref<VoteResultData | null>(null)
  const selectedSeat = ref<number | null>(null)
  const connected = ref(false)
  const loaded = ref(false)

  const mySeat = computed(() => privateState.value?.mySeat ?? -1)
  const alive = computed(() => privateState.value?.alive ?? false)
  const actions = computed(() => privateState.value?.availableActions ?? [])

  /** 现在这一步需要我选一个目标吗 */
  const needTarget = computed(() =>
    actions.value.some((a) =>
      ['HUNTER_SHOOT', 'WEREWOLF_KILL', 'SEER_CHECK', 'WITCH_POISON', 'VOTE'].includes(a)
    )
  )

  function pushLog(text: string, kind: GameLog['kind'] = 'system') {
    if (!text) {
      return
    }
    const now = new Date()
    const pad = (n: number) => String(n).padStart(2, '0')
    logs.value.push({
      time: `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`,
      text,
      kind
    })
    if (logs.value.length > 120) {
      logs.value.shift()
    }
  }

  /** 阶段推进的先后顺序（用来判断一份快照是不是"退回去了"） */
  const PHASE_ORDER: Record<string, number> = {
    WAITING: 0,
    NIGHT: 1,
    DAY: 2,
    HUNTER_SHOOT: 3,
    DAY_DISCUSSION: 4,
    DAY_VOTE: 5,
    FINISHED: 6
  }

  /**
   * 装一份公开状态。
   *
   * 之前用过 stateSeq 版本号来"丢旧快照"，但那个判断有个天然坑：
   * 同一批事件里 PHASE_CHANGE 的版本号总比 GAME_STATE_UPDATE 小 1
   * （前者在状态推进**之前**生成、后者在**之后**组装），
   * 任何"比当前小就丢"的规则都可能误杀 PHASE_CHANGE ——
   * 而 PHASE_CHANGE 才是"阶段刚变"的那条，被误杀的表现就是
   * "右侧日志已经到女巫了，上方提示还停在狼人，两三秒后才追上"。
   *
   * 现在改成**按语义判断**：只有"同一轮里阶段倒退"才算过期快照
   * （比如白天收到一份写着夜晚的）。跨轮次的一律接受，
   * 因为新的一轮 NIGHT 在数值上比上一轮 FINISHED 小，但那不是过期。
   */
  function applyPublicState(next?: PublicGameState) {
    if (!next) {
      return
    }
    const current = publicState.value
    if (current && next.round === current.round) {
      const before = PHASE_ORDER[current.phase] ?? 0
      const now = PHASE_ORDER[next.phase] ?? 0
      if (now < before) {
        return // 同一轮里阶段倒退了 —— 这才是迟到的旧快照
      }
    }
    publicState.value = next
  }

  /** 处理服务器推来的消息 */
  function onWsMessage(message: WsMessage) {
    switch (message.type) {
      case 'ROLE_ASSIGN':
        roleCard.value = message.data as RoleAssignData
        pushLog(message.message || '你拿到了身份牌', 'system')
        break

      case 'GAME_STATE_UPDATE': {
        const view = message.data as GameView
        // 用统一的入口装状态：它会丢掉版本号更低的旧快照
        applyPublicState(view?.publicState)
        if (view?.privateState) {
          privateState.value = view.privateState
          // 选中的目标如果已经不能选了（死了 / 不是这个阶段），清掉选中
          if (selectedSeat.value != null) {
            const seat = view.publicState?.seats.find((s) => s.seatNo === selectedSeat.value)
            if (!seat || !seat.alive || !needTarget.value) {
              selectedSeat.value = null
            }
          }
        }
        break
      }

      case 'PHASE_CHANGE': {
        // ★ 阶段一变，立刻把消息里带的公开状态装进来。
        //
        // 后端在 PHASE_CHANGE 的 payload 里放了完整的 publicState（含 nightActor、
        // 轮次、存活座位…），之前这里只 pushLog 就完事了 —— 等于把它丢掉了，
        // 导致"夜晚提示慢半拍"：后端已经切到下一个角色，界面还显示上一个。
        applyPublicState(message.data as PublicGameState | undefined)
        pushLog(message.message || '阶段变化', 'phase')
        break
      }

      case 'NIGHT_RESULT':
        pushLog(message.message || '夜晚结果', 'death')
        break

      case 'PLAYER_DEAD':
        pushLog(message.message || '有玩家出局', 'death')
        break

      case 'PLAYER_SPEAK': {
        const data = message.data as { nickname?: string; text?: string }
        pushLog(`${data?.nickname ?? '?'}：${data?.text ?? ''}`, 'speak')
        break
      }

      case 'VOTE_UPDATE':
        pushLog(message.message || '有人投票', 'vote')
        break

      case 'VOTE_RESULT': {
        // 投票结果：给所有人弹一下"谁出局了" —— 光滚在日志里很容易被忽略
        voteResult.value = (message.data ?? {}) as VoteResultData
        pushLog(message.message || '投票结果', 'vote')
        break
      }

      case 'GAME_END':
        endResult.value = message.data as GameEndData
        pushLog(message.message || '游戏结束', 'system')
        break

      case 'HOST_SAY': {
        // 主持人的解说：进日志，同时在界面上单独醒目显示一下
        const data = (message.data ?? {}) as { text?: string; moment?: string }
        const text = data.text || message.message || ''
        if (text) {
          hostLine.value = { text, moment: data.moment || '' }
          pushLog('🎙 ' + text, 'host')
        }
        break
      }

      case 'AI_REFLECTION': {
        // 局后复盘：AI 反省自己这一局。结算面板收到后补上这段
        const data = (message.data ?? {}) as { reflections?: Record<string, string> }
        reflections.value = data.reflections ?? {}
        break
      }

      case 'ERROR':
        ElMessage.warning(message.message || '操作失败')
        break

      default:
        // ROOM_UPDATE 之类的消息在这里不需要处理
        break
    }
  }

  /** 进入游戏页：拉一次最新视图 + 订阅房间频道和私人频道 */
  async function enterGame(id: number) {
    const auth = useAuthStore()
    if (!auth.token) {
      throw new Error('未登录')
    }

    gameId.value = id
    endResult.value = null
    logs.value = []
    selectedSeat.value = null

    const view = (await getGame(id)).data
    publicState.value = view.publicState
    privateState.value = view.privateState
    roomId.value = view.publicState.roomId
    loaded.value = true

    // 把身份牌补回来。
    // ★ 必须补：ROLE_ASSIGN 那条消息在开局时就发过了，刷新页面 / 晚点进游戏页的人
    //   根本收不到它 —— 之前就是因此导致"刷新后看不到自己的身份卡"。
    //   私人状态里本来就带着身份信息，从这儿重建一份最省事。
    if (view.privateState && view.privateState.myRole) {
      roleCard.value = {
        seatNo: view.privateState.mySeat,
        role: view.privateState.myRole,
        roleLabel: view.privateState.myRoleLabel,
        camp: view.privateState.myCamp,
        teammateSeats: view.privateState.teammateSeats
      }
    }

    await wsClient.connect(auth.token)
    connected.value = wsClient.isConnected()
    wsClient.subscribeUser(onWsMessage)
    wsClient.subscribeRoom(view.publicState.roomId, onWsMessage)
  }

  /** 离开游戏页 */
  function exitGame() {
    wsClient.unsubscribeRoom()
    wsClient.unsubscribeUser()
    gameId.value = null
    roomId.value = null
    publicState.value = null
    privateState.value = null
    roleCard.value = null
    endResult.value = null
    logs.value = []
    selectedSeat.value = null
    connected.value = false
    loaded.value = false
  }

  /** 发一条行动给服务器（统一走 /app/action） */
  function send(type: string, data: Record<string, unknown> = {}) {
    const payload = { ...data, gameId: gameId.value }
    const ok = wsClient.send(type, payload)
    if (!ok) {
      ElMessage.warning('连接已断开，正在重连，请稍后再试')
    }
    return ok
  }

  /** 点座位（只在需要选目标时生效） */
  function selectSeat(seatNo: number) {
    if (!needTarget.value || !alive.value) {
      return
    }
    const seat = publicState.value?.seats.find((s) => s.seatNo === seatNo)
    if (!seat || !seat.alive || seatNo === mySeat.value) {
      return
    }
    selectedSeat.value = selectedSeat.value === seatNo ? null : seatNo
  }

  /** 确认对选中目标执行当前动作 */
  function confirmTarget() {
    const target = selectedSeat.value
    if (target == null) {
      ElMessage.warning('请先点击一个座位选择目标')
      return
    }
    const act = actions.value.find((a) =>
      ['HUNTER_SHOOT', 'WEREWOLF_KILL', 'SEER_CHECK', 'WITCH_POISON', 'VOTE'].includes(a))
    if (!act) {
      return
    }
    if (send(act, { targetSeat: target })) {
      selectedSeat.value = null
    }
  }

  /** 女巫用解药 */
  function useSave() {
    send('WITCH_SAVE')
  }

  /** 女巫放弃用药 */
  function pass() {
    send('PASS')
  }

  /** 发言 */
  function speak(text: string) {
    const content = text.trim()
    if (!content) {
      ElMessage.warning('发言内容不能为空')
      return false
    }
    return send('SPEAK', { text: content })
  }

  return {
    gameId,
    roomId,
    publicState,
    privateState,
    roleCard,
    logs,
    endResult,
    hostLine,
    reflections,
    voteResult,
    selectedSeat,
    connected,
    loaded,
    mySeat,
    alive,
    actions,
    needTarget,
    enterGame,
    exitGame,
    selectSeat,
    confirmTarget,
    useSave,
    pass,
    speak,
    pushLog
  }
})
