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

async function handleLogin() {
  if (!username.value.trim() || !password.value) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  try {
    const result = await auth.login(username.value.trim(), password.value)
    ElMessage.success(`欢迎回来，${result.user.nickname}`)
    // 从别的页面被拦过来时，登录后回到原来想去的页面
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  }
}

function goRegister() {
  router.push({ path: '/register', query: route.query })
}
</script>

<template>
  <div class="auth-page">
    <el-card class="panel">
      <h1 class="title">AI Table</h1>
      <p class="subtitle">登录</p>

      <el-form class="form" label-width="70px" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="username" placeholder="你的账号" maxlength="50" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            show-password
            placeholder="你的密码"
            maxlength="32"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="submit" :loading="auth.loading" @click="handleLogin">登录</el-button>
        </el-form-item>
      </el-form>

      <div class="switch">
        <el-button link type="primary" @click="router.push('/forgot-password')">忘记密码？</el-button>
      </div>
      <div class="switch">
        还没有账号？
        <el-button link type="primary" @click="goRegister">去注册</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding: 70px 16px;
}

.panel {
  width: 100%;
  max-width: 460px;
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
