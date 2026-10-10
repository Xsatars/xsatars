import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from '@/types'

/** 登录 token 的存储 key */
const TOKEN_KEY = 'ai-table-token'

/**
 * 取当前登录 token。
 *
 * <p>优先取<b>本窗口</b>的（sessionStorage），其次才是全局的（localStorage）。</p>
 *
 * <p>为什么要这样：同一个浏览器的多个普通窗口 / 多个无痕窗口是<b>共享 localStorage</b> 的，
 * 想开 6 个窗口分别登录 6 个账号时会互相覆盖——A 窗口里显示自己是房主，
 * 实际发出去的请求却带着 B 的身份，于是就会出现"提示我不是房主"这种怪事。
 * 把 token 同时写一份到 sessionStorage（每个标签页独立），就能各登各的互不干扰。</p>
 */
export function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY) ?? localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (token) {
    sessionStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    sessionStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(TOKEN_KEY)
  }
}

/**
 * axios 实例。
 *
 * baseURL 用相对路径 /api，开发环境由 vite.config.ts 里的 proxy 转发到后端 8080，
 * 所以这里不需要写死后端地址。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：自动带上登录 token
request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

/**
 * "登录已过期"只弹一次。
 *
 * <p>为什么需要：一个页面往往会同时发好几个请求（拉房间 + 拉游戏状态 + 拉性格列表…），
 * token 过期时它们会**一起**收到 401，于是弹出好几个一模一样的提示 ——
 * 用户看到的就是"登录失效"连弹三次。</p>
 */
let lastAuthExpiredAt = 0
const AUTH_EXPIRED_INTERVAL = 3000

function warnAuthExpired() {
  const now = Date.now()
  if (now - lastAuthExpiredAt < AUTH_EXPIRED_INTERVAL) {
    return
  }
  lastAuthExpiredAt = now
  ElMessage.warning('登录已过期，请重新登录')
}

// 响应拦截器：统一处理业务错误码，避免每个页面都写一遍判断
request.interceptors.response.use(
  (response) => {
    const result = response.data as Result<unknown> | undefined
    if (result && typeof result.code === 'number' && result.code !== 200) {
      if (result.code === 401) {
        // 登录过期：清掉本地 token，页面自己决定要不要跳回首页
        setToken(null)
        warnAuthExpired()
      } else {
        ElMessage.error(result.message || '请求失败')
      }
      return Promise.reject(new Error(result.message || '请求失败'))
    }
    return response
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      // HTTP 401 也是登录过期（后端没包成 Result 的情况）
      setToken(null)
      warnAuthExpired()
    } else {
      const message = status
        ? `请求失败（HTTP ${status}）`
        : '无法连接后端服务，请确认后端已启动'
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default request
