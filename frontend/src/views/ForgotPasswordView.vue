<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { resetPassword } from '@/api/user'

/**
 * 忘记密码。
 *
 * 用「用户名 + 注册时填的昵称」证明这个号是你的，然后直接设新密码。
 * （没有邮箱 / 短信服务时的简化做法，正式上线应该换成验证码。）
 */
const router = useRouter()

const username = ref('')
const nickname = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)

async function handleSubmit() {
  if (!username.value.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!nickname.value.trim()) {
    ElMessage.warning('请输入注册时填的昵称')
    return
  }
  if (newPassword.value.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    ElMessage.warning('两次输入的密码不一样')
    return
  }

  submitting.value = true
  try {
    await resetPassword(username.value.trim(), nickname.value.trim(), newPassword.value)
    ElMessage.success('密码已重置，请用新密码登录')
    router.push('/login')
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="forgot-page">
    <el-card class="card">
      <template #header>
        <div class="head">找回密码</div>
      </template>

      <el-alert
        class="hint"
        type="info"
        :closable="false"
        show-icon
        title="怎么找回？"
        description="填上你的用户名，以及注册时填的昵称（就是游戏里显示的名字），验证通过后就能直接设置新密码。"
      />

      <el-form label-width="90px" class="form">
        <el-form-item label="用户名">
          <el-input v-model="username" placeholder="注册时用的用户名" @keyup.enter="handleSubmit" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="nickname" placeholder="注册时填的昵称" @keyup.enter="handleSubmit" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input
            v-model="newPassword"
            type="password"
            show-password
            placeholder="6-32 位"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input
            v-model="confirmPassword"
            type="password"
            show-password
            placeholder="再输一遍新密码"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
      </el-form>

      <div class="actions">
        <el-button type="primary" :loading="submitting" @click="handleSubmit">重置密码</el-button>
        <el-button @click="router.push('/login')">返回登录</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.forgot-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 16px;
  background: rgba(255, 255, 255, 0.06);
}

.card {
  width: 480px;
  max-width: 100%;
}

.head {
  font-size: 18px;
  font-weight: bold;
  text-align: center;
}

.hint {
  margin-bottom: 18px;
}

.form {
  margin-top: 8px;
}

.actions {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-top: 6px;
}
</style>
