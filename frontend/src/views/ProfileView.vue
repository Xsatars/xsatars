<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearAvatar, updateNickname, updatePassword, uploadAvatar } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import UserAvatar from '@/components/UserAvatar.vue'

/**
 * 个人主页：改昵称、改密码、换头像。
 */
const router = useRouter()
const auth = useAuthStore()

const nickname = ref('')
const savingNickname = ref(false)
const uploadingAvatar = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const savingPassword = ref(false)

onMounted(async () => {
  await auth.restore()
  nickname.value = auth.user?.nickname ?? ''
})

function pickAvatar() {
  fileInput.value?.click()
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 清空掉，方便连续选同一个文件
  input.value = ''
  if (!file) {
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('图片太大了，请选 2MB 以内的图片')
    return
  }

  uploadingAvatar.value = true
  try {
    const res = await uploadAvatar(file)
    auth.setUser(res.data)
    ElMessage.success('头像已更新')
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    uploadingAvatar.value = false
  }
}

async function handleClearAvatar() {
  try {
    await ElMessageBox.confirm('清除后头像会变回默认的空白头像，确定吗？', '提示', {
      confirmButtonText: '清除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    const res = await clearAvatar()
    auth.setUser(res.data)
    ElMessage.success('头像已清除')
  } catch (e) {
    // 拦截器已提示
  }
}

async function handleSaveNickname() {
  const value = nickname.value.trim()
  if (!value) {
    ElMessage.warning('昵称不能为空')
    return
  }
  if (value === auth.user?.nickname) {
    ElMessage.info('昵称没有变化')
    return
  }
  savingNickname.value = true
  try {
    const res = await updateNickname(value)
    auth.setUser(res.data)
    ElMessage.success('昵称已更新')
  } catch (e) {
    // 拦截器已提示
  } finally {
    savingNickname.value = false
  }
}

async function handleSavePassword() {
  if (!oldPassword.value) {
    ElMessage.warning('请输入原密码')
    return
  }
  if (newPassword.value.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    ElMessage.warning('两次输入的新密码不一样')
    return
  }

  savingPassword.value = true
  try {
    await updatePassword(oldPassword.value, newPassword.value)
    ElMessage.success('密码已修改')
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (e) {
    // 拦截器已提示
  } finally {
    savingPassword.value = false
  }
}

function goHome() {
  router.push('/')
}
</script>

<template>
  <div class="profile-page">
    <el-card class="card">
      <template #header>
        <div class="head">
          <span>个人主页</span>
          <el-button link @click="goHome">返回首页</el-button>
        </div>
      </template>

      <!-- 头像 -->
      <div class="avatar-row">
        <UserAvatar :src="auth.user?.avatar" :name="auth.user?.nickname" :size="88" />
        <div class="avatar-actions">
          <div class="nick">{{ auth.user?.nickname || '（未登录）' }}</div>
          <div class="account">账号：{{ auth.user?.username }}</div>
          <div class="btns">
            <el-button size="small" :loading="uploadingAvatar" @click="pickAvatar">
              上传头像
            </el-button>
            <el-button v-if="auth.user?.avatar" size="small" @click="handleClearAvatar">
              清除头像
            </el-button>
            <input
              ref="fileInput"
              type="file"
              accept="image/png,image/jpeg,image/gif,image/webp"
              style="display: none"
              @change="onFileChange"
            />
          </div>
          <div class="tip">支持 png / jpg / gif / webp，2MB 以内。不上传就显示默认的空白头像。</div>
        </div>
      </div>

      <el-divider />

      <!-- 改昵称 -->
      <div class="block">
        <div class="block-title">修改昵称</div>
        <div class="row">
          <el-input v-model="nickname" maxlength="20" show-word-limit placeholder="新的昵称" />
          <el-button type="primary" :loading="savingNickname" @click="handleSaveNickname">
            保存
          </el-button>
        </div>
        <div class="tip">游戏里、房间里显示的就是这个昵称。</div>
      </div>

      <el-divider />

      <!-- 改密码 -->
      <div class="block">
        <div class="block-title">修改密码</div>
        <el-form label-width="90px" class="pwd-form">
          <el-form-item label="原密码">
            <el-input v-model="oldPassword" type="password" show-password placeholder="当前的密码" />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="newPassword" type="password" show-password placeholder="6-32 位" />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input
              v-model="confirmPassword"
              type="password"
              show-password
              placeholder="再输一遍新密码"
              @keyup.enter="handleSavePassword"
            />
          </el-form-item>
        </el-form>
        <el-button type="primary" :loading="savingPassword" @click="handleSavePassword">
          修改密码
        </el-button>
        <div class="tip">改完之后当前登录不受影响，下次用新密码登录。</div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.profile-page {
  max-width: 620px;
  margin: 0 auto;
  padding: 16px;
}

.card {
  margin-top: 12px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.avatar-row {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.avatar-actions .nick {
  font-size: 18px;
  font-weight: bold;
}

.avatar-actions .account {
  color: var(--at-text-secondary);
  font-size: 13px;
  margin: 4px 0 10px;
}

.avatar-actions .btns {
  display: flex;
  gap: 8px;
  align-items: center;
}

.block-title {
  font-weight: bold;
  margin-bottom: 12px;
}

.row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.pwd-form {
  max-width: 420px;
}

.tip {
  color: var(--at-text-secondary);
  font-size: 12px;
  margin-top: 8px;
  line-height: 1.6;
}
</style>
