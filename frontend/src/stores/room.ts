import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import {
  addAiPlayer as addAiApi,
  createRoom as createRoomApi,
  getRoom as getRoomApi,
  joinRoom as joinRoomApi,
  kickPlayer as kickPlayerApi,
  leaveRoom as leaveRoomApi,
  removeAiPlayer as removeAiApi,
  setReady as setReadyApi,
  startGame as startGameApi
} from '@/api/room'
import { useAuthStore } from './auth'
import { wsClient } from '@/ws/client'
import type { PlayerVO, RoomVO, WsMessage } from '@/types'

/**
 * 房间状态。
 *
 * 分工：
 *   - 写操作（创建/加入/离开）走 HTTP，因为有明确的成功失败返回
 *   - 读更新走 WebSocket，别人加入/离开时自动收到 ROOM_UPDATE，界面实时刷新
 */
export const useRoomStore = defineStore('room', () => {
  const room = ref<RoomVO | null>(null)
  const wsConnected = ref(false)

  /** WebSocket 收到的消息都在这里处理 */
  function onWsMessage(message: WsMessage) {
    switch (message.type) {
      case 'ROOM_UPDATE':
        // 服务器推来的房间状态直接替换，前端不做任何"猜测"
        room.value = message.data as RoomVO
        break
      case 'ERROR':
        ElMessage.warning(message.message || '操作失败')
        break
      default:
        // 游戏相关的消息在 Step 5-6 之后处理
        console.log('[ws] 暂未处理的消息:', message)
    }
  }

  /** 进入房间：先连 WebSocket（服务端才知道我在线），再拉最新房间信息 */
  async function enterRoom(roomIdOrNo: string | number) {
    const auth = useAuthStore()
    if (!auth.token) {
      throw new Error('未登录')
    }

    // 先连上再查数据：否则拉数据时服务端还没登记这条连接，
    // 房间里会出现"我明明在房间里却显示离线"（要等别人进来触发广播才变在线）
    await wsClient.connect(auth.token)
    wsConnected.value = wsClient.isConnected()

    const loaded = (await getRoomApi(roomIdOrNo)).data
    room.value = loaded

    wsClient.subscribeUser(onWsMessage)
    wsClient.subscribeRoom(loaded.id, onWsMessage)
    return loaded
  }

  /** 离开房间页（只取消订阅，不断开连接） */
  function exitRoom() {
    wsClient.unsubscribeRoom()
    wsClient.unsubscribeUser()
    room.value = null
    wsConnected.value = false
  }

  async function createRoom(name?: string, boardCode?: string) {
    const res = await createRoomApi(name, boardCode)
    room.value = res.data
    return res.data
  }

  async function joinRoom(roomNo: string) {
    const res = await joinRoomApi(roomNo)
    room.value = res.data
    return res.data
  }

  async function loadRoom(roomIdOrNo: string | number) {
    const res = await getRoomApi(roomIdOrNo)
    room.value = res.data
    return res.data
  }

  /** 离开当前房间 */
  async function leaveCurrentRoom() {
    const current = room.value
    if (!current) {
      return
    }
    const mine = myPlayer()
    if (!mine) {
      exitRoom()
      return
    }
    await leaveRoomApi(current.id, mine.playerId)
    exitRoom()
  }

  /** 房主开始游戏，返回新对局ID */
  async function startGame(roomId: number): Promise<number> {
    const res = await startGameApi(roomId)
    return res.data
  }

  /** 准备 / 取消准备（房主不需要准备） */
  async function setReady(ready: boolean) {
    const current = room.value
    if (!current) {
      return
    }
    const res = await setReadyApi(current.id, ready)
    room.value = res.data
  }

  /** 房主把某个成员踢出房间 */
  async function kickPlayer(playerId: number) {
    const current = room.value
    if (!current) {
      return
    }
    const res = await kickPlayerApi(current.id, playerId)
    room.value = res.data
  }

  /** 房主加一个 AI 玩家补位（一个人也能开局）；persona 用来指定性格 */
  async function addAiPlayer(persona?: string) {
    const current = room.value
    if (!current) {
      return
    }
    const res = await addAiApi(current.id, persona)
    room.value = res.data
  }

  /** 房主移除一个 AI 玩家 */
  async function removeAiPlayer(playerId: number) {
    const current = room.value
    if (!current) {
      return
    }
    const res = await removeAiApi(current.id, playerId)
    room.value = res.data
  }

  /** 我在当前房间里的席位（不在房间里返回 null） */
  function myPlayer(): PlayerVO | null {    const auth = useAuthStore()
    return room.value?.players.find((p) => p.userId === auth.user?.id) ?? null
  }

  /** 我是不是房主 */
  function isOwner(): boolean {
    const auth = useAuthStore()
    return room.value?.ownerUserId === auth.user?.id
  }

  return {
    room,
    wsConnected,
    enterRoom,
    exitRoom,
    createRoom,
    joinRoom,
    loadRoom,
    leaveCurrentRoom,
    startGame,
    setReady,
    kickPlayer,
    addAiPlayer,
    removeAiPlayer,
    myPlayer,
    isOwner
  }
})
