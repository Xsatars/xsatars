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
  WsMessage
} from '@/types'

/** 一条游戏公告（右侧滚动显示） */
export interface GameLog {
  time: string
  text: string
  kind: 'system' | 'phase' | 'death' | 'speak' | 'vote'
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
  const selectedSeat = ref<number | null>(null)
  const connected = ref(false)
  const loaded = ref(false)

  const mySeat = computed(() => privateState.value?.mySeat ?? -1)
  const alive = computed(() => privateState.value?.alive ?? false)
  const actions = computed(() => privateState.value?.availableActions ?? [])

  /** 现在这一步需要我选一个目标吗 */
  const needTarget = computed(() =>
    actions.value.some((a) => ['WEREWOLF_KILL', 'SEER_CHECK', 'WITCH_POISON', 'VOTE'].includes(a))
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

  /** 处理服务器推来的消息 */
  function onWsMessage(message: WsMessage) {
    switch (message.type) {
      case 'ROLE_ASSIGN':
        roleCard.value = message.data as RoleAssignData
        pushLog(message.message || '你拿到了身份牌', 'system')
        break

      case 'GAME_STATE_UPDATE': {
        const view = message.data as GameView
        if (view?.publicState) {
          publicState.value = view.publicState
        }
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

      case 'PHASE_CHANGE':
        pushLog(message.message || '阶段变化', 'phase')
        break

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

      case 'VOTE_RESULT':
        pushLog(message.message || '投票结果', 'vote')
        break

      case 'GAME_END':
        endResult.value = message.data as GameEndData
        pushLog(message.message || '游戏结束', 'system')
        break

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
      ['WEREWOLF_KILL', 'SEER_CHECK', 'WITCH_POISON', 'VOTE'].includes(a))
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
