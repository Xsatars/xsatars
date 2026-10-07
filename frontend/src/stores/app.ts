import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 全局应用状态（示例 store）。
 *
 * 用法（在组件里）：
 *   const appStore = useAppStore()
 *   appStore.title
 *
 * 后续会按业务拆分 store，例如：
 *   - stores/user.ts   当前登录用户
 *   - stores/room.ts   房间与座位
 *   - stores/game.ts   游戏状态（我的视角）
 */
export const useAppStore = defineStore('app', () => {
  const title = ref('AI Table')
  const subtitle = ref('多人在线 AI 桌游平台')

  function setTitle(value: string) {
    title.value = value
  }

  return { title, subtitle, setTitle }
})
