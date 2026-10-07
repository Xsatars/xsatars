<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const nickname = ref('')

/** 提交前先在前端做一遍校验，避免白跑一趟后端 */
function validate(): string | null {
  const name = username.value.trim()
  if (!/^[a-zA-Z0-9_]{3,50}$/.test(name)) {
    return '用户名需要 3-50 个字符，只能用字母、数字和下划线'
  }
  if (password.value.length < 6 || password.value.length > 32) {
    return '密码长度需要在 6-32 个字符之间'
  }
  if (password.value !== confirmPassword.value) {
    return '两次输入的密码不一致'
  }
  if (!nickname.value.trim()) {
    return '请填写昵称（游戏里显示的名字）'
  }
  if (nickname.value.trim().length > 50) {
    return '昵称不能超过 50 个字符'
  }
  return null
}

async function handleRegister() {
  const error = validate()
  if (error) {
    ElMessage.warning(error)
    return
  }
  try {
    const result = await auth.register(username.value.trim(), password.value, nickname.value.trim())
    ElMessage.success(`注册成功，欢迎你，${result.user.nickname}！`)
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  }
}

function goLogin() {
  router.push({ path: '/login', query: route.query })
}
</script>

<template>
  <div class="auth-page">
    <el-card class="panel">
      <h1 class="title">AI Table</h1>
      <p class="subtitle">注册新账号</p>

      <el-form class="form" label-width="80px" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="username" placeholder="3-50 位字母、数字或下划线" maxlength="50" @keyup.enter="handleRegister" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            show-password
            placeholder="6-32 位"
            maxlength="32"
            @keyup.enter="handleRegister"
          />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input
            v-model="confirmPassword"
            type="password"
            show-password
            placeholder="再输入一次密码"
            maxlength="32"
            @keyup.enter="handleRegister"
          />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="nickname" placeholder="游戏里显示的名字" maxlength="50" @keyup.enter="handleRegister" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="submit" :loading="auth.loading" @click="handleRegister">
            注册并登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="switch">
        已经有账号了？
        <el-button link type="primary" @click="goLogin">去登录</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding: 50px 16px;
}

.panel {
  width: 100%;
  max-width: 480px;
}

.title {
  margin: 0 0 4px;
  font-size: 26px;
}

.subtitle {
  margin: 0 0 20px;
  color: #909399;
}

.form {
  margin-top: 10px;
}

.submit {
  width: 100%;
}

.switch {
  color: #606266;
  font-size: 14px;
}
</style>
