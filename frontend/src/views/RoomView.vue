<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useRoomStore } from '@/stores/room'
import UserAvatar from '@/components/UserAvatar.vue'
import type { PlayerVO } from '@/types'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const roomStore = useRoomStore()

const loading = ref(true)
const starting = ref(false)
const readying = ref(false)
const errorMessage = ref('')

const roomIdOrNo = computed(() => String(route.params.roomId))
const room = computed(() => roomStore.room)
const isOwner = computed(() => roomStore.isOwner())
const myPlayer = computed(() => roomStore.myPlayer())

/** 把座位号 1..6 都列出来，空位也能显示 */
const seatList = computed(() => {
  const max = room.value?.maxPlayers ?? 6
  const players = room.value?.players ?? []
  const list: { seat: number; player: PlayerVO | null }[] = []
  for (let seat = 1; seat <= max; seat++) {
    list.push({ seat, player: players.find((p) => p.seatNo === seat) ?? null })
  }
  return list
})

const statusText = computed(() => {
  switch (room.value?.status) {
    case 'WAITING':
      return '等待开始'
    case 'PLAYING':
      return '游戏进行中'
    case 'FINISHED':
      return '已结束'
    default:
      return room.value?.status ?? ''
  }
})

const statusType = computed(() => {
  switch (room.value?.status) {
    case 'WAITING':
      return 'success'
    case 'PLAYING':
      return 'warning'
    default:
      return 'info'
  }
})

/** 房主视角：还差什么才能开局 */
const startHint = computed(() => {
  const current = room.value
  if (!current) {
    return ''
  }
  const missing = current.maxPlayers - current.playerCount
  if (missing > 0) {
    return `还需要 ${missing} 名玩家`
  }
  const notReady = current.players.filter((p) => !p.owner && !p.ready)
  if (notReady.length > 0) {
    return `还有 ${notReady.length} 人没准备：${notReady.map((p) => p.nickname).join('、')}`
  }
  return ''
})

onMounted(async () => {
  try {
    await roomStore.enterRoom(roomIdOrNo.value)
  } catch (e) {
    errorMessage.value = e instanceof Error && e.message === '未登录' ? '请先登录' : '房间不存在或已结束'
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  roomStore.exitRoom()
})

// 房主开局后，房间状态会变成 PLAYING 并带上对局ID —— 房间里所有人都自动进入游戏页
watch(
  () => roomStore.room,
  (value) => {
    if (value?.status === 'PLAYING' && value.currentGameId) {
      router.push(`/game/${value.currentGameId}`)
    }
  }
)

async function handleLeave() {
  try {
    await ElMessageBox.confirm('确定要离开这个房间吗？', '提示', {
      confirmButtonText: '离开',
      cancelButtonText: '再想想',
      type: 'warning'
    })
  } catch (e) {
    return // 用户点了取消
  }
  try {
    await roomStore.leaveCurrentRoom()
    ElMessage.success('已离开房间')
  } catch (e) {
    // 拦截器已提示
  }
  router.push('/')
}

async function handleToggleReady() {  readying.value = true
  try {
    await roomStore.setReady(!myPlayer.value?.ready)
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    readying.value = false
  }
}

/** 房主把某个成员踢出房间 */
async function handleKick(player: PlayerVO) {
  try {
    await ElMessageBox.confirm(`确定把「${player.nickname}」移出房间吗？`, '踢出成员', {
      confirmButtonText: '踢出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await roomStore.kickPlayer(player.playerId)
    ElMessage.success(`已把「${player.nickname}」移出房间`)
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  }
}

async function handleStartGame() {
  if (!room.value) {
    return
  }
  starting.value = true
  try {
    const gameId = await roomStore.startGame(room.value.id)
    router.push(`/game/${gameId}`)
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    starting.value = false
  }
}
</script>

<template>
  <div class="room-page">
    <!-- 加载中 -->
    <el-card v-if="loading" class="panel">
      <el-skeleton :rows="4" animated />
    </el-card>

    <!-- 出错 -->
    <el-card v-else-if="!room" class="panel">
      <el-alert type="error" :closable="false" show-icon :title="errorMessage || '房间不存在'" />
      <el-button class="back" @click="router.push('/')">返回首页</el-button>
    </el-card>

    <!-- 正常 -->
    <el-card v-else class="panel">
      <div class="head">
        <div>
          <h1 class="room-name">{{ room.name }}</h1>
          <div class="meta">
            <span class="label">房间号</span>
            <span class="room-no">{{ room.roomNo }}</span>
            <el-tag :type="statusType" size="small">{{ statusText }}</el-tag>
            <el-tag :type="roomStore.wsConnected ? 'success' : 'warning'" size="small">
              {{ roomStore.wsConnected ? '实时连接正常' : '连接中…' }}
            </el-tag>
          </div>
        </div>
        <el-button @click="handleLeave">离开房间</el-button>
      </div>

      <el-alert
        v-if="!room.canStart"
        class="hint-bar"
        type="info"
        :closable="false"
        show-icon
        :title="room.playerCount < room.maxPlayers
          ? `等待玩家加入（${room.playerCount}/${room.maxPlayers}）`
          : '人齐了，等待所有人准备'"
        :description="room.playerCount < room.maxPlayers
          ? '把房间号发给朋友，他们用同一个网址登录后输入房间号即可加入。'
          : `${startHint}。除房主外每个人都要点「准备」，房主才能开局。`"
      />

      <div class="seats">
        <div
          v-for="item in seatList"
          :key="item.seat"
          class="seat"
          :class="{
            empty: !item.player,
            me: item.player && item.player.userId === auth.user?.id,
            owner: item.player?.owner
          }"
        >
          <div class="seat-no">{{ item.seat }} 号位</div>
          <template v-if="item.player">
            <div class="seat-main">
              <UserAvatar :src="item.player.avatar" :name="item.player.nickname" :size="36" />
              <div class="seat-name">{{ item.player.nickname }}</div>
            </div>
            <div class="seat-tags">
              <el-tag v-if="item.player.owner" size="small" type="warning">房主</el-tag>
              <el-tag v-if="item.player.userId === auth.user?.id" size="small">我</el-tag>
              <el-tag v-if="!item.player.online" size="small" type="info">离线</el-tag>
              <el-tag
                v-if="!item.player.owner"
                size="small"
                :type="item.player.ready ? 'success' : 'info'"
                :effect="item.player.ready ? 'dark' : 'plain'"
              >
                {{ item.player.ready ? '已准备' : '未准备' }}
              </el-tag>
              <!-- 房主可以把别人请出房间 -->
              <el-button
                v-if="isOwner && item.player.userId !== auth.user?.id"
                link
                type="danger"
                size="small"
                @click="handleKick(item.player)"
              >
                踢出
              </el-button>
            </div>
          </template>
          <div v-else class="seat-empty">等待加入…</div>
        </div>
      </div>

      <div class="footer">
        <template v-if="isOwner">
          <el-button
            type="primary"
            size="large"
            :loading="starting"
            :disabled="!room.canStart"
            @click="handleStartGame"
          >
            开始游戏
          </el-button>
          <span v-if="!room.canStart" class="hint">{{ startHint }}</span>
          <span v-else class="hint">人齐且都准备好了，可以开局</span>
        </template>
        <template v-else>
          <el-button
            :type="myPlayer?.ready ? 'default' : 'success'"
            size="large"
            :loading="readying"
            @click="handleToggleReady"
          >
            {{ myPlayer?.ready ? '取消准备' : '准备' }}
          </el-button>
          <span class="hint">
            {{ myPlayer?.ready ? '已准备，等待房主开始游戏…' : '点「准备」，房主才能开始游戏' }}
          </span>
        </template>
      </div>

      <div class="me-info">
        我的身份：{{ myPlayer?.nickname }}（{{ myPlayer?.seatNo }} 号位{{ myPlayer?.owner ? ' · 房主' : '' }}）
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.room-page {
  display: flex;
  justify-content: center;
  padding: 40px 16px;
}

.panel {
  width: 100%;
  max-width: 780px;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.room-name {
  margin: 0 0 10px;
  font-size: 22px;
}

.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #606266;
}

.label {
  font-size: 13px;
  color: #909399;
}

.room-no {
  font-size: 20px;
  font-weight: bold;
  letter-spacing: 2px;
  color: #409eff;
}

.hint-bar {
  margin: 18px 0;
}

.seats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin: 18px 0;
}

.seat {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 14px;
  min-height: 96px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: #fff;
}

.seat.me {
  border-color: #409eff;
  background: #ecf5ff;
}

.seat.owner {
  border-color: #e6a23c;
}

.seat.empty {
  border-style: dashed;
  color: #c0c4cc;
  background: #fafafa;
}

.seat-no {
  font-size: 12px;
  color: #909399;
}

.seat-main {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 6px 0;
}

.seat-name {
  font-size: 15px;
  font-weight: bold;
  word-break: break-all;
}

.seat-tags {
  display: flex;
  gap: 6px;
}

.seat-empty {
  font-size: 14px;
  color: #c0c4cc;
}

.footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.hint {
  color: #909399;
  font-size: 13px;
}

.me-info {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
  color: #606266;
  font-size: 13px;
}

.back {
  margin-top: 16px;
}
</style>
