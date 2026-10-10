<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCurrentGame } from '@/api/game'
import { getBoards } from '@/api/room'
import UserAvatar from '@/components/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'
import { useRoomStore } from '@/stores/room'
import type { BoardVO, GameView } from '@/types'

const router = useRouter()
const auth = useAuthStore()
const roomStore = useRoomStore()

const roomNo = ref('')
const creating = ref(false)
const joining = ref(false)
/** 还没结束的对局（中途退出后靠它回去） */
const ongoingGame = ref<GameView | null>(null)

/** 可选的板子（问后端要，不硬编码） */
const boards = ref<BoardVO[]>([])
/** 选中的板子代号 */
const boardCode = ref('WEREWOLF_6')
/** 选模式的弹窗（点"创建房间"才打开） */
const showCreateDialog = ref(false)

function openCreateDialog() {
  showCreateDialog.value = true
}

onMounted(async () => {
  // 路由守卫已经保证登录了，这里再把用户信息补全（刷新页面后需要）
  await auth.restore()
  await checkOngoingGame()
  await loadBoards()
})

/** 拉一下有哪些板子可玩 */
async function loadBoards() {
  try {
    boards.value = (await getBoards()).data ?? []
    if (boards.value.length && !boards.value.some((b) => b.code === boardCode.value)) {
      boardCode.value = boards.value[0].code
    }
  } catch (e) {
    // 拉不到就用默认 6 人局，不挡着建房
  }
}

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
    const room = await roomStore.createRoom(undefined, boardCode.value)
    showCreateDialog.value = false
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
      <!-- 品牌区 -->
      <div class="brand">
        <div class="brand-mark">🎭</div>
        <h1 class="brand-name">AI Table</h1>
        <p class="brand-tagline">多人在线 AI 桌游 · 狼人杀</p>
      </div>

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
        <el-button type="primary" size="large" @click="openCreateDialog">创建房间</el-button>
        <div class="tip">创建后你会成为房主，并自动坐在 1 号位</div>
      </div>

      <!-- 选模式：点"创建房间"才弹出来，选完再建 -->
      <el-dialog v-model="showCreateDialog" title="选择模式" width="400px" align-center>
        <div class="board-list">
          <div
            v-for="b in boards"
            :key="b.code"
            class="board-item"
            :class="{ picked: boardCode === b.code }"
            @click="boardCode = b.code"
          >
            <span class="board-label">{{ b.label }}</span>
            <span class="board-count">{{ b.playerCount }} 人</span>
          </div>
          <div v-if="!boards.length" class="board-item picked">
            <span class="board-label">标准 6 人局</span>
            <span class="board-count">6 人</span>
          </div>
        </div>
        <template #footer>
          <el-button @click="showCreateDialog = false">取消</el-button>
          <el-button type="primary" :loading="creating" @click="handleCreateRoom">
            创建房间
          </el-button>
        </template>
      </el-dialog>

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
  align-items: flex-start;
  min-height: 100%;
  padding: 7vh 16px 60px;
}

.panel {
  width: 100%;
  max-width: 620px;
}

/* 品牌区：渐变大标题 + 带光晕的 emoji */
.brand {
  text-align: center;
  margin-bottom: 24px;
}

.brand-mark {
  font-size: 46px;
  line-height: 1;
  margin-bottom: 12px;
  filter: drop-shadow(0 0 22px rgba(129, 140, 248, 0.55));
}

.brand-name {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 1px;
  /* 渐变文字：用 background-clip 把渐变裁进字形 */
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

.userbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: var(--at-radius-sm);
  background: var(--at-bg-soft);
}

.nick {
  font-weight: bold;
}

.account {
  color: var(--at-text-secondary);
  font-size: 13px;
}

.block-title {
  margin-bottom: 12px;
  color: var(--at-text-regular);
}

/* 选模式的弹窗：一行一个模式，点一下选中 */
.board-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.board-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border: 1px solid rgba(255, 255, 255, 0.13);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.board-item:hover {
  border-color: rgba(129, 140, 248, 0.4);
  background: rgba(129, 140, 248, 0.12);
}

.board-item.picked {
  border-color: #818cf8;
  background: rgba(129, 140, 248, 0.18);
}

.board-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--at-text);
}

.board-count {
  font-size: 13px;
  color: var(--at-text-secondary);
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
  color: var(--at-text-secondary);
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
  color: var(--at-text-secondary);
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
