<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useGameStore } from '@/stores/game'
import UserAvatar from '@/components/UserAvatar.vue'
import type { SeatView } from '@/types'

const route = useRoute()
const router = useRouter()
const game = useGameStore()

const loading = ref(true)
const errorMessage = ref('')
const speakText = ref('')
const showResult = ref(false)

const gameId = computed(() => Number(route.params.gameId))
const publicState = computed(() => game.publicState)
const privateState = computed(() => game.privateState)
const seats = computed<SeatView[]>(() => publicState.value?.seats ?? [])
const needTarget = computed(() => game.needTarget)

/** 狼队各自刀了谁（只有狼人看得到） */
const wolfVoteText = computed(() => {
  const votes = (privateState.value?.info?.wolfVotes ?? {}) as Record<string, number>
  const seats = Object.keys(votes)
  if (seats.length === 0) {
    return ''
  }
  return seats
    .map((seat) => {
      const who = Number(seat) === mySeatNo.value ? '你' : `${seat} 号`
      return `${who} → ${votes[seat]} 号`
    })
    .join('　｜　')
})

const wolfTargetSeat = computed(() => {
  const target = privateState.value?.info?.wolfTarget
  return typeof target === 'number' ? target : null
})

/** 服务器下发的"我现在能做什么"，前端只照着渲染，不自己判断规则 */
function can(action: string) {
  return game.actions.includes(action)
}

/** 每秒刷新一次，用来算倒计时 */
const now = ref(Date.now())
let tickTimer: number | undefined

/** 距离这一步超时还有多少秒；不计时返回 null */
const remainingSeconds = computed(() => {
  const deadline = publicState.value?.deadline ?? 0
  if (!deadline) {
    return null
  }
  return Math.max(0, Math.ceil((deadline - now.value) / 1000))
})

/** 轮到我操作、而且快没时间了，就把倒计时标红 */
const countdownUrgent = computed(() => {
  const seconds = remainingSeconds.value
  const busy = needTarget.value || can('SPEAK') || can('PASS')
  return seconds != null && seconds <= 10 && busy
})

/** 不同阶段"跳过"按钮的文案不一样 */
const passLabel = computed(() => {
  const phase = publicState.value?.phase
  if (phase === 'DAY_DISCUSSION') return '放弃发言'
  if (phase === 'DAY_VOTE') return '弃权（不投任何人）'
  return '今晚不用药'
})

const targetLabel = computed(() => {
  if (can('WEREWOLF_KILL')) return '击杀'
  if (can('SEER_CHECK')) return '查验'
  if (can('WITCH_POISON')) return '毒杀'
  if (can('VOTE')) return '投票放逐'
  return '确认'
})

const phaseTagType = computed(() => {
  switch (publicState.value?.phase) {
    case 'NIGHT':
      return 'info'
    case 'DAY_DISCUSSION':
      return 'warning'
    case 'DAY_VOTE':
      return 'danger'
    case 'FINISHED':
      return 'success'
    default:
      return 'primary'
  }
})

const mySeatNo = computed(() => privateState.value?.mySeat ?? -1)
const teammates = computed(() => privateState.value?.teammateSeats ?? [])

/** 座位上的标记 */
function seatClasses(seat: SeatView) {
  return {
    dead: !seat.alive,
    offline: seat.alive && !seat.online,
    me: seat.seatNo === mySeatNo.value,
    speaking: seat.speaking,
    teammate: teammates.value.includes(seat.seatNo),
    selectable: needTarget.value && seat.alive && seat.seatNo !== mySeatNo.value,
    selected: game.selectedSeat === seat.seatNo
  }
}

onMounted(async () => {
  tickTimer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
  try {
    await game.enterGame(gameId.value)
  } catch (e) {
    errorMessage.value = e instanceof Error && e.message === '未登录' ? '请先登录' : '对局不存在或已经结束'
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  if (tickTimer) {
    window.clearInterval(tickTimer)
  }
  game.exitGame()
})

watch(
  () => game.endResult,
  (value) => {
    if (value) {
      showResult.value = true
    }
  },
  { immediate: true }
)

function handleSpeak() {
  if (game.speak(speakText.value)) {
    speakText.value = ''
  }
}

async function handleLeave() {
  try {
    await ElMessageBox.confirm('离开这个页面不会退出对局，你可以稍后再回来。确定离开吗？', '提示', {
      confirmButtonText: '离开',
      cancelButtonText: '留下',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  router.push('/')
}

function goHome() {
  router.push('/')
}

/** 游戏结束后留在房间里，方便马上再来一局 */
function stayInRoom() {
  const roomId = publicState.value?.roomId
  if (roomId) {
    router.push(`/room/${roomId}`)
  } else {
    goHome()
  }
}
</script>

<template>
  <div class="game-page">
    <!-- 加载中 -->
    <el-card v-if="loading" class="panel">
      <el-skeleton :rows="6" animated />
    </el-card>

    <!-- 出错 -->
    <el-card v-else-if="!publicState" class="panel">
      <el-alert type="error" :closable="false" show-icon :title="errorMessage || '对局不存在'" />
      <el-button class="back" @click="goHome">返回首页</el-button>
    </el-card>

    <!-- 游戏界面 -->
    <template v-else>
      <!-- 顶部：阶段 / 我的身份 / 连接状态 -->
      <div class="topbar">
        <div class="phase-box">
          <span class="round">第 {{ publicState.round }} 轮</span>
          <el-tag :type="phaseTagType" size="large" effect="dark">{{ publicState.phaseLabel }}</el-tag>
          <span class="alive-info">存活 {{ publicState.aliveCount }} 人</span>
          <el-tag v-if="remainingSeconds != null" :type="countdownUrgent ? 'danger' : 'info'" size="small">
            ⏱ 剩余 {{ remainingSeconds }} 秒
          </el-tag>
        </div>
        <div class="my-box" v-if="privateState">
          <el-tag :type="privateState.myCamp === 'WOLF' ? 'danger' : 'success'" effect="dark">
            {{ mySeatNo }} 号 · {{ privateState.myRoleLabel }}
          </el-tag>
          <el-tag v-if="teammates.length" type="danger" effect="plain">
            狼队友：{{ teammates.join('、') }} 号
          </el-tag>
          <el-tag v-if="!privateState.alive" type="info">你已出局</el-tag>
        </div>
        <div class="conn-box">
          <el-tag :type="game.connected ? 'success' : 'warning'" size="small">
            {{ game.connected ? '实时连接正常' : '重连中…' }}
          </el-tag>
          <el-button link @click="handleLeave">离开</el-button>
        </div>
      </div>

      <div class="main">
        <!-- 左边：座位桌 + 操作区 -->
        <div class="left">
          <el-card class="card">
            <template #header>
              <span>座位</span>
              <span v-if="needTarget" class="header-tip">点击一个座位来选择目标</span>
            </template>

            <div class="seats">
              <div
                v-for="seat in seats"
                :key="seat.seatNo"
                class="seat"
                :class="seatClasses(seat)"
                @click="game.selectSeat(seat.seatNo)"
              >
                <div class="seat-head">
                  <span class="seat-no">{{ seat.seatNo }} 号</span>
                  <el-tag v-if="seat.seatNo === mySeatNo" size="small">我</el-tag>
                  <el-tag v-if="teammates.includes(seat.seatNo)" size="small" type="danger">狼队友</el-tag>
                  <el-tag v-if="seat.alive && !seat.online" size="small" type="info">离线</el-tag>
                </div>
                <div class="seat-main">
                  <UserAvatar :src="seat.avatar" :name="seat.nickname" :size="30" />
                  <div class="seat-name">{{ seat.nickname }}</div>
                </div>
                <div class="seat-foot">
                  <el-tag v-if="!seat.alive" size="small" type="info">已出局</el-tag>
                  <el-tag v-else-if="seat.speaking" size="small" type="warning">发言中</el-tag>
                  <el-tag v-else-if="seat.acted" size="small" type="success">已行动</el-tag>
                  <el-tag v-else size="small" type="info" effect="plain">存活</el-tag>
                  <span v-if="seat.voteCount > 0" class="votes">{{ seat.voteCount }} 票</span>
                </div>
              </div>
            </div>
          </el-card>

          <!-- 操作区 -->
          <el-card class="card action-card">
            <div class="hint">{{ privateState?.hint || '等待其他玩家…' }}</div>

            <div class="actions">
              <!-- 需要选目标：狼刀 / 查验 / 毒杀 / 投票 -->
              <el-button
                v-if="needTarget"
                type="primary"
                size="large"
                :disabled="game.selectedSeat == null"
                @click="game.confirmTarget"
              >
                {{ targetLabel }}{{ game.selectedSeat ? `（${game.selectedSeat} 号）` : '（请先点座位）' }}
              </el-button>

              <!-- 女巫 -->
              <el-button v-if="can('WITCH_SAVE')" type="success" size="large" @click="game.useSave">
                使用解药救人
              </el-button>
              <el-button v-if="can('PASS')" size="large" @click="game.pass">{{ passLabel }}</el-button>

              <!-- 发言 -->
              <template v-if="can('SPEAK')">
                <el-input
                  v-model="speakText"
                  class="speak-input"
                  placeholder="轮到你发言了，说点什么…"
                  maxlength="200"
                  show-word-limit
                  @keyup.enter="handleSpeak"
                />
                <el-button type="primary" size="large" @click="handleSpeak">发言</el-button>
              </template>

              <span
                v-if="!needTarget && !can('WITCH_SAVE') && !can('PASS') && !can('SPEAK')"
                class="waiting"
              >
                {{ privateState?.alive ? '这一刻不需要你操作，等待其他玩家…' : '你已出局，可以继续旁观' }}
              </span>
            </div>

            <!-- 我的私有信息 -->
            <div v-if="privateState" class="private-info">
              <template v-if="privateState.myRole === 'WOLF'">
                <span>狼队刀人：</span>
                <span v-if="wolfVoteText">{{ wolfVoteText }}</span>
                <span v-else class="muted">还没有人选目标</span>
                <el-tag v-if="wolfTargetSeat" type="danger" size="small">
                  今晚目标：{{ wolfTargetSeat }} 号
                </el-tag>
              </template>
              <template v-if="privateState.myRole === 'WITCH'">
                <span>解药：{{ privateState.info.saveUsed ? '已用' : '可用' }}</span>
                <span>毒药：{{ privateState.info.poisonUsed ? '已用' : '可用' }}</span>
                <span v-if="privateState.info.wolfTargetSeat">
                  今晚被袭击的是 <b>{{ privateState.info.wolfTargetSeat }} 号</b>
                </span>
              </template>
              <template v-if="privateState.myRole === 'SEER'">
                <span>查验记录：</span>
                <span v-if="Object.keys(privateState.info.seerResults || {}).length === 0">暂无</span>
                <el-tag
                  v-for="(isWolf, seat) in (privateState.info.seerResults || {})"
                  :key="seat"
                  :type="isWolf ? 'danger' : 'success'"
                  size="small"
                >
                  {{ seat }} 号：{{ isWolf ? '狼人' : '好人' }}
                </el-tag>
              </template>
            </div>
          </el-card>
        </div>

        <!-- 右边：公告区 -->
        <div class="right">
          <el-card class="card log-card">
            <template #header><span>对局记录</span></template>
            <div class="logs">
              <div v-for="(log, index) in game.logs" :key="index" class="log" :class="log.kind">
                <span class="log-time">{{ log.time }}</span>
                <span class="log-text">{{ log.text }}</span>
              </div>
              <div v-if="game.logs.length === 0" class="log-empty">暂无记录</div>
            </div>
          </el-card>
        </div>
      </div>
    </template>

    <!-- 结算弹窗 -->
    <el-dialog v-model="showResult" title="本局结束" width="620px" :close-on-click-modal="false">
      <template v-if="game.endResult">
        <el-result
          :icon="game.endResult.winnerCamp === 'DRAW' ? 'info' : 'success'"
          :title="game.endResult.winnerLabel"
          :sub-title="`共进行 ${game.endResult.rounds} 轮`"
        />
        <el-table :data="game.endResult.players" border size="small">
          <el-table-column prop="seatNo" label="座位" width="70" />
          <el-table-column prop="nickname" label="玩家" min-width="120" />
          <el-table-column label="身份" width="100">
            <template #default="{ row }">
              <el-tag :type="row.camp === 'WOLF' ? 'danger' : 'success'" size="small">{{ row.roleLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="结局" width="90">
            <template #default="{ row }">{{ row.alive ? '存活' : '出局' }}</template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="stayInRoom">留在房间，再来一局</el-button>
        <el-button type="primary" @click="goHome">回到首页</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.game-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 16px;
}

.panel {
  margin-top: 40px;
}

.back {
  margin-top: 16px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  margin-bottom: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.phase-box,
.my-box,
.conn-box {
  display: flex;
  align-items: center;
  gap: 10px;
}

.round {
  font-weight: bold;
  color: #303133;
}

.alive-info {
  color: #909399;
  font-size: 13px;
}

.main {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 12px;
  align-items: start;
}

@media (max-width: 900px) {
  .main {
    grid-template-columns: 1fr;
  }
}

.card {
  margin-bottom: 12px;
}

.header-tip {
  margin-left: 10px;
  color: #e6a23c;
  font-size: 13px;
}

.seats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.seat {
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
  transition: all 0.15s;
  cursor: default;
}

.seat.selectable {
  cursor: pointer;
  border-color: #c6e2ff;
  background: #fafcff;
}

.seat.selectable:hover {
  border-color: #409eff;
}

.seat.selected {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.15);
}

.seat.me {
  border-color: #67c23a;
}

.seat.teammate {
  border-color: #f56c6c;
}

.seat.speaking {
  background: #fdf6ec;
  border-color: #e6a23c;
}

.seat.dead {
  opacity: 0.55;
  background: #f5f5f5;
  border-style: dashed;
}

.seat.offline {
  background: #fafafa;
  border-style: dotted;
}

.seat-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.seat-no {
  font-size: 12px;
  color: #909399;
}

.seat-name {
  font-size: 16px;
  font-weight: bold;
  margin: 6px 0;
  word-break: break-all;
}

.seat-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.seat-main .seat-name {
  margin: 0;
  font-size: 15px;
}

.seat-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.votes {
  color: #f56c6c;
  font-weight: bold;
  font-size: 13px;
}

.action-card .hint {
  color: #606266;
  margin-bottom: 12px;
  min-height: 22px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.speak-input {
  flex: 1;
  min-width: 240px;
}

.waiting {
  color: #909399;
  font-size: 14px;
}

.private-info {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed #ebeef5;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  color: #606266;
  font-size: 13px;
}

.logs {
  max-height: 520px;
  overflow-y: auto;
}

.log {
  display: flex;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed #f2f6fc;
  font-size: 13px;
  line-height: 1.5;
}

.log-time {
  color: #c0c4cc;
  flex-shrink: 0;
}

.log.phase .log-text {
  color: #409eff;
  font-weight: bold;
}

.log.death .log-text {
  color: #f56c6c;
}

.log.speak .log-text {
  color: #303133;
}

.log.vote .log-text {
  color: #e6a23c;
}

.log-empty {
  color: #c0c4cc;
  font-size: 13px;
  text-align: center;
  padding: 20px 0;
}

.muted {
  color: #c0c4cc;
}
</style>
