import { ref } from 'vue'
import { defineStore } from 'pinia'
import { login as loginApi, me as meApi, register as registerApi } from '@/api/auth'
import { getToken, setToken } from '@/api/request'
import type { LoginVO, UserVO } from '@/types'

/**
 * 登录状态。
 *
 * token 存在 localStorage，所以刷新页面不会掉登录；
 * 刷新后用 /api/users/me 把用户信息拉回来。
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserVO | null>(null)
  const token = ref<string | null>(getToken())
  const loading = ref(false)

  /** 把登录结果写进状态（注册和登录都走这里） */
  function applyLoginResult(result: LoginVO) {
    token.value = result.token
    user.value = result.user
    setToken(result.token)
    return result
  }

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const res = await loginApi({ username, password })
      return applyLoginResult(res.data)
    } finally {
      loading.value = false
    }
  }

  async function register(username: string, password: string, nickname: string) {
    loading.value = true
    try {
      const res = await registerApi({ username, password, nickname })
      return applyLoginResult(res.data)
    } finally {
      loading.value = false
    }
  }

  /** 页面刷新后恢复登录状态，返回是否仍然登录着 */
  async function restore(): Promise<boolean> {
    if (!getToken()) {
      return false
    }
    try {
      const res = await meApi()
      user.value = res.data
      token.value = getToken()
      return true
    } catch (e) {
      logout()
      return false
    }
  }

  function logout() {
    user.value = null
    token.value = null
    setToken(null)
  }

  /** 个人资料改过之后（改昵称、换头像），把最新的用户信息同步到状态里 */
  function setUser(value: UserVO) {
    user.value = value
  }

  return { user, token, loading, login, register, restore, logout, setUser }
})
