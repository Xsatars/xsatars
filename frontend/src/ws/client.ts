import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import { ElMessage } from 'element-plus'
import { setToken } from '@/api/request'
import type { WsMessage } from '@/types'

type MessageHandler = (message: WsMessage) => void

/**
 * 本页面的随机标识（存在 sessionStorage，每个标签页独立）。
 *
 * 服务端靠它区分"同一个账号的哪个页面"：同一个账号在两个页面登录时，
 * 后来者顶替先来者，旧页面会收到 SESSION_REPLACED 然后自动退出。
 */
function getSessionKey(): string {
  const KEY = 'ai-table-session'
  let key = sessionStorage.getItem(KEY)
  if (!key) {
    key = Math.random().toString(36).slice(2) + Date.now().toString(36)
    sessionStorage.setItem(KEY, key)
  }
  return key
}

/**
 * WebSocket 客户端（STOMP 协议）封装。
 *
 * 整个应用共用这一个连接：
 *   - 公共频道 /topic/room/{roomId} —— 房间里的座位变化
 *   - 私人频道 /user/queue/game     —— 只发给自己的消息（自己的角色等）
 *
 * token 通过地址参数传给后端（浏览器的 WebSocket 没法加自定义请求头），
 * 后端在握手阶段就会校验。
 */
class WsClient {
  private client: Client | null = null

  private roomSubscription: StompSubscription | null = null

  private userSubscription: StompSubscription | null = null

  /** 订阅房间频道的人（通常是 room store） */
  private roomHandlers = new Set<MessageHandler>()

  /** 订阅私人频道的人 */
  private userHandlers = new Set<MessageHandler>()

  /** 连接状态（给界面显示"已连接/断线中"用） */
  private connected = false

  private connectPromise: Promise<void> | null = null

  /** 当前订阅的房间ID */
  private roomId: number | null = null

  /** 是否已经被"账号在别处登录"踢掉（避免重复提示） */
  private kicked = false

  isConnected(): boolean {
    return this.connected
  }

  /** 建立连接；已经连上就直接返回 */
  connect(token: string): Promise<void> {
    if (this.connected) {
      return Promise.resolve()
    }
    if (this.connectPromise) {
      return this.connectPromise
    }

    this.connectPromise = new Promise<void>((resolve, reject) => {
      const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
      const url = `${protocol}://${window.location.host}/ws?token=${encodeURIComponent(token)}`

      const client = new Client({
        brokerURL: url,
        // 本页面的标识：服务端靠它识别"同一个账号的哪个页面"
        connectHeaders: {
          'x-session-key': getSessionKey()
        },
        // 断线后自动重连
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
          this.connected = true
          this.connectPromise = null
          // 重连后原来的订阅已经失效，需要重新订阅
          this.resubscribe()
          resolve()
        },
        onWebSocketError: () => {
          this.connectPromise = null
          reject(new Error('无法连接游戏服务器，请确认后端已启动'))
        },
        onStompError: (frame) => {
          console.error('STOMP 协议错误:', frame.headers['message'], frame.body)
        },
        onWebSocketClose: () => {
          this.connected = false
        }
      })

      this.client = client
      client.activate()
    })

    return this.connectPromise
  }

  /** 订阅房间公共频道 */
  subscribeRoom(roomId: number, handler: MessageHandler) {
    this.roomHandlers.add(handler)
    this.roomId = roomId
    if (this.client && this.connected) {
      this.doSubscribeRoom()
    }
  }

  /** 把之前订阅的房间取消掉 */
  unsubscribeRoom(handler?: MessageHandler) {
    if (handler) {
      this.roomHandlers.delete(handler)
    } else {
      this.roomHandlers.clear()
    }
    if (this.roomHandlers.size === 0 && this.roomSubscription) {
      this.roomSubscription.unsubscribe()
      this.roomSubscription = null
      this.roomId = null
    }
  }

  /** 订阅私人频道（自己的角色、错误提示等） */
  subscribeUser(handler: MessageHandler) {
    this.userHandlers.add(handler)
    if (this.client && this.connected) {
      this.doSubscribeUser()
    }
  }

  unsubscribeUser(handler?: MessageHandler) {
    if (handler) {
      this.userHandlers.delete(handler)
    } else {
      this.userHandlers.clear()
    }
    // 一定要真的把订阅取消掉：只清 handler 的话，STOMP 订阅还活着，
    // 从房间页跳到游戏页时会叠出两个订阅，同一条消息就被处理两次
    if (this.userHandlers.size === 0 && this.userSubscription) {
      this.userSubscription.unsubscribe()
      this.userSubscription = null
    }
  }

  /** 发送一条消息到后端（统一入口 /app/action） */
  send(type: string, data?: unknown) {
    if (!this.client || !this.connected) {
      console.warn('WebSocket 未连接，消息未发送:', type)
      return false
    }
    this.client.publish({
      destination: '/app/action',
      body: JSON.stringify({ type, data: data ?? {} })
    })
    return true
  }

  /** 主动断开连接（离开房间页时调用） */
  disconnect() {
    this.roomHandlers.clear()
    this.userHandlers.clear()
    this.roomSubscription = null
    this.userSubscription = null
    this.roomId = null
    this.connected = false
    this.connectPromise = null
    const client = this.client
    this.client = null
    if (client) {
      client.deactivate()
    }
  }

  // ---------------- 内部实现 ----------------

  private doSubscribeRoom() {
    if (this.roomId === null || !this.client) {
      return
    }
    this.cancelRoomSubscription()
    this.roomSubscription = this.client.subscribe(`/topic/room/${this.roomId}`, (frame) => {
      this.dispatch(frame, this.roomHandlers)
    })
  }

  private doSubscribeUser() {
    if (!this.client) {
      return
    }
    this.cancelUserSubscription()
    this.userSubscription = this.client.subscribe('/user/queue/game', (frame) => {
      this.dispatch(frame, this.userHandlers)
    })
  }

  /** 取消房间订阅（重复订阅会让同一条消息收到两次） */
  private cancelRoomSubscription() {
    if (this.roomSubscription) {
      try {
        this.roomSubscription.unsubscribe()
      } catch (e) {
        // 连接已经断了的话取消会报错，忽略即可
      }
      this.roomSubscription = null
    }
  }

  private cancelUserSubscription() {
    if (this.userSubscription) {
      try {
        this.userSubscription.unsubscribe()
      } catch (e) {
        // 连接已经断了的话取消会报错，忽略即可
      }
      this.userSubscription = null
    }
  }

  /** 重连之后把订阅重新建立起来 */
  private resubscribe() {
    if (this.roomHandlers.size > 0) {
      this.doSubscribeRoom()
    }
    if (this.userHandlers.size > 0) {
      this.doSubscribeUser()
    }
  }

  private dispatch(frame: IMessage, handlers: Set<MessageHandler>) {
    let message: WsMessage
    try {
      message = JSON.parse(frame.body) as WsMessage
    } catch (e) {
      console.warn('收到无法解析的 WebSocket 消息:', frame.body)
      return
    }

    // 同一个账号只允许一个页面在玩：被顶替的旧页面自行退出
    if (message.type === 'SESSION_REPLACED') {
      const data = message.data as { sessionKey?: string } | undefined
      if (!data?.sessionKey || data.sessionKey !== getSessionKey()) {
        this.handleSessionReplaced(message.message)
      }
      return
    }

    handlers.forEach((handler) => handler(message))
  }

  /** 账号在别处登录了：清登录态、断开连接、回登录页 */
  private handleSessionReplaced(reason?: string) {
    if (this.kicked) {
      return
    }
    this.kicked = true
    setToken(null)
    this.disconnect()
    ElMessage.warning(reason || '你的账号已在其他页面登录，当前页面已退出')
    window.setTimeout(() => {
      window.location.href = '/login'
    }, 1500)
  }
}

/** 全局唯一的 WebSocket 客户端 */
export const wsClient = new WsClient()
