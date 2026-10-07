<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCurrentGame } from '@/api/game'
import UserAvatar from '@/components/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'
import { useRoomStore } from '@/stores/room'
import type { GameView } from '@/types'

const router = useRouter()
const auth = useAuthStore()
const roomStore = useRoomStore()

const roomNo = ref('')
const creating = ref(false)
const joining = ref(false)
/** 还没结束的对局（中途退出后靠它回去） */
const ongoingGame = ref<GameView | null>(null)

onMounted(async () => {
  // 路由守卫已经保证登录了，这里再把用户信息补全（刷新页面后需要）
  await auth.restore()
  await checkOngoingGame()
})

/** 看看我有没有还没打完的对局（关掉页面/断网之后回来用） */
async function checkOngoingGame() {
  try {
    const res = await getCurrentGame()
    ongoingGame.value = res.data
  } catch (e) {
    ongoingGame.value = null
  }
}

function backToGame() {
  if (ongoingGame.value) {
    router.push(`/game/${ongoingGame.value.publicState.gameId}`)
  }
}

async function handleCreateRoom() {
  creating.value = true
  try {
    const room = await roomStore.createRoom()
    ElMessage.success('房间已创建，房间号 ' + room.roomNo)
    router.push(`/room/${room.roomNo}`)
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    creating.value = false
  }
}

async function handleJoinRoom() {
  const no = roomNo.value.trim()
  if (!/^\d{6}$/.test(no)) {
    ElMessage.warning('请输入 6 位房间号')
    return
  }
  joining.value = true
  try {
    await roomStore.joinRoom(no)
    router.push(`/room/${no}`)
  } catch (e) {
    // 同上
  } finally {
    joining.value = false
  }
}

function handleLogout() {
  auth.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

function goAdmin() {
  router.push('/admin')
}
</script>

<template>
  <div class="home">
    <el-card class="panel">
      <h1 class="title">AI Table</h1>
      <p class="subtitle">多人在线 AI 桌游平台 · 狼人杀</p>

      <div class="userbar">
        <UserAvatar :src="auth.user?.avatar" :name="auth.user?.nickname" :size="40" />
        <div class="who">
          <div class="nick">{{ auth.user?.nickname }}</div>
          <div class="account">{{ auth.user?.username }}</div>
        </div>
        <el-button link type="primary" @click="router.push('/profile')">个人主页</el-button>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </div>

      <el-divider />

      <!-- 有没打完的对局：直接回去接着玩 -->
      <el-alert
        v-if="ongoingGame"
        class="ongoing"
        type="warning"
        :closable="false"
        show-icon
        :title="`你有一局还没打完的对局（第 ${ongoingGame.publicState.round} 轮 · ${ongoingGame.publicState.phaseLabel}）`"
        :description="`你的身份：${ongoingGame.privateState.mySeat} 号 · ${ongoingGame.privateState.myRoleLabel}`"
      >
        <template #default>
          <div class="ongoing-body">
            <span>你的身份：{{ ongoingGame.privateState.mySeat }} 号 · {{ ongoingGame.privateState.myRoleLabel }}</span>
            <el-button type="primary" size="small" @click="backToGame">回到对局</el-button>
          </div>
        </template>
      </el-alert>

      <div class="block">
        <div class="block-title">创建一个新房间</div>
        <el-button type="primary" size="large" :loading="creating" @click="handleCreateRoom">
          创建房间（6 人局）
        </el-button>
        <div class="tip">创建后你会成为房主，并自动坐在 1 号位</div>
      </div>

      <el-divider />

      <div class="block">
        <div class="block-title">加入已有房间</div>
        <div class="join-row">
          <el-input
            v-model="roomNo"
            placeholder="输入 6 位房间号"
            maxlength="6"
            class="join-input"
            @keyup.enter="handleJoinRoom"
          />
          <el-button size="large" :loading="joining" @click="handleJoinRoom">加入房间</el-button>
        </div>
        <div class="tip">房间号由房主分享给你，例如 123456</div>
      </div>

      <template v-if="auth.user?.role === 'ADMIN'">
        <el-divider />
        <div class="status">
          <el-button link type="primary" @click="goAdmin">进入管理后台</el-button>
          <span class="tip">（数据概览 / 用户管理 / 房间管理 / 游戏记录）</span>
        </div>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  justify-content: center;
  padding: 50px 16px;
}

.panel {
  width: 100%;
  max-width: 620px;
}

.title {
  margin: 0 0 4px;
  font-size: 28px;
}

.subtitle {
  margin: 0 0 20px;
  color: #909399;
}

.userbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nick {
  font-weight: bold;
}

.account {
  color: #909399;
  font-size: 13px;
}

.block-title {
  margin-bottom: 12px;
  color: #606266;
}

.join-row {
  display: flex;
  gap: 10px;
}

.join-input {
  max-width: 220px;
}

.tip {
  margin-top: 8px;
  color: #909399;
  font-size: 13px;
}

.nick {
  font-weight: bold;
}

.who {
  line-height: 1.3;
  margin-right: auto;
}

.who .account {
  font-size: 12px;
  color: #909399;
}

.userbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ongoing {
  margin-bottom: 4px;
}

.ongoing-body {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
}
</style>
