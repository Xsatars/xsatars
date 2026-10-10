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
      <!-- 品牌区：给登录页一点"这是个产品"的感觉，而不是一个光秃秃的表单 -->
      <div class="brand">
        <div class="brand-mark">🎭</div>
        <h1 class="brand-name">AI Table</h1>
        <p class="brand-tagline">和 AI 一起，开一局狼人杀</p>
      </div>

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
  align-items: flex-start;
  min-height: 100%;
  padding: 8vh 16px 60px;
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
  color: var(--at-text-secondary);
}

/* 品牌区：渐变大标题 + 带光晕的 emoji */
.brand {
  text-align: center;
  margin-bottom: 26px;
}

.brand-mark {
  font-size: 46px;
  line-height: 1;
  margin-bottom: 12px;
  /* 给 emoji 一点"舞台光" */
  filter: drop-shadow(0 0 22px rgba(129, 140, 248, 0.55));
}

.brand-name {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 1px;
  /* 渐变文字：靠 background-clip 把渐变裁进字形里 */
  background: linear-gradient(135deg, #a5b4fc 0%, #c4b5fd 55%, #7dd3fc 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.brand-tagline {
  margin: 8px 0 0;
  color: var(--at-text-secondary);
  font-size: 14px;
}

.form {
  margin-top: 10px;
}

.submit {
  width: 100%;
}

.switch {
  color: var(--at-text-regular);
  font-size: 14px;
}
</style>
