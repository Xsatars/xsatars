<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, onUpdated, ref, watch } from 'vue'
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

/**
 * 夜晚的行动提示条。
 *
 * 后端只说"轮到哪个角色"（WOLF / WITCH / SEER），**图标、配色、文案全在前端定** ——
 * 这样以后加新角色（比如守卫）只要改这张表，后端不用动。
 *
 * 提示条里绝对不能出现"目标是谁"，那才是要藏起来的信息。
 */
const nightBanner = computed(() => {
  const actor = publicState.value?.nightActor
  if (!actor) {
    return null
  }
  const table: Record<string, { icon: string; text: string; sub: string; cls: string }> = {
    WOLF: { icon: '🐺', text: '狼人正在行动', sub: '正在商量今晚要杀谁…', cls: 'wolf' },
    WITCH: { icon: '🧪', text: '女巫正在行动', sub: '在解药和毒药之间做选择…', cls: 'witch' },
    SEER: { icon: '🔮', text: '预言家正在行动', sub: '正在查验一个人的身份…', cls: 'seer' }
  }
  return table[actor] ?? { icon: '🌙', text: '夜晚正在推进', sub: '', cls: '' }
})
const privateState = computed(() => game.privateState)
const seats = computed<SeatView[]>(() => publicState.value?.seats ?? [])
const needTarget = computed(() => game.needTarget)

/**
 * AI 的复盘列表（把"座位号 → 内容"转成能渲染的数组，按座位排序）。
 *
 * 复盘是 GAME_END 之后才生成的（要调大模型，慢一点），所以刚弹出结算面板时
 * 可能还没有 —— 那时候界面显示"正在复盘…"。
 */
const reflectionList = computed(() => {
  const map = game.reflections ?? {}
  const players = game.endResult?.players ?? []
  return Object.entries(map)
    .map(([seat, text]) => {
      const seatNo = Number(seat)
      const player = players.find((p) => p.seatNo === seatNo)
      return { seatNo, nickname: player?.nickname ?? '', text }
    })
    .sort((a, b) => a.seatNo - b.seatNo)
})

/** 结算面板已经弹出来，但复盘还没生成 */
const waitingReflection = computed(
  () => !!game.endResult && reflectionList.value.length === 0
)

// ---------- 开局的身份牌弹窗 ----------

/** 发完牌自动弹一次，让每个人一眼看清自己是谁、该干什么 */
const showRoleCard = ref(false)

/** 各角色的开场说明（不熟规则的人看这一段就够上手了） */
const ROLE_INTRO: Record<string, { emoji: string; desc: string }> = {
  WOLF: {
    emoji: '🐺',
    desc: '每晚和狼队友一起选一个人杀掉。白天要装成好人，别被投票放逐。'
  },
  SEER: {
    emoji: '🔮',
    desc: '每晚可以查验一个人的身份（好人还是狼人）。你是好人的主心骨，也最容易被狼盯上。'
  },
  WITCH: {
    emoji: '🧪',
    desc: '你有一瓶解药一瓶毒药，各只能用一次。解药能救下当晚被刀的人 —— 也包括你自己。'
  },
  HUNTER: {
    emoji: '🔫',
    desc: '你平时没有技能，但被狼刀死或被投票放逐时，可以开枪带走一个人。'
      + '注意：被女巫毒死是开不了枪的。'
  },
  VILLAGER: {
    emoji: '👤',
    desc: '你没有特殊能力，靠发言和投票把狼人找出来。'
  }
}

const roleIntro = computed(
  () =>
    ROLE_INTRO[game.roleCard?.role ?? ''] ?? {
      emoji: '🃏',
      desc: '看好自己的身份，别暴露给狼人。'
    }
)

const roleTeammates = computed(() => game.roleCard?.teammateSeats ?? [])

// ---------- 投票出局提示 ----------

/** 投票结果弹窗（每次 VOTE_RESULT 都会跳出来，给所有人看） */
const showVoteResult = ref(false)

const voteResultEmoji = computed(() => {
  const r = game.voteResult
  if (!r || (!r.exiledSeat && !r.tie)) return '🤷'
  if (r.tie) return '⚖️'
  return '🗳️'
})

const voteResultText = computed(() => {
  const r = game.voteResult
  if (!r) {
    return ''
  }
  if (r.exiledSeat) {
    return `${r.exiledSeat} 号 ${r.exiledNickname ?? ''} 被投票放逐`
  }
  if (r.tie) {
    return '平票，本轮无人出局'
  }
  return '没有人投票，本轮无人出局'
})

/** 票型明细，比如"3 号：2 票　5 号：1 票" */
const voteResultDetail = computed(() => {
  const counts = game.voteResult?.voteCounts
  if (!counts) {
    return ''
  }
  const entries = Object.entries(counts).sort((a, b) => b[1] - a[1])
  if (!entries.length) {
    return ''
  }
  return entries.map(([seat, n]) => `${seat} 号：${n} 票`).join('　')
})

// 一有投票结果就弹出来
watch(
  () => game.voteResult,
  (v) => {
    if (v) {
      showVoteResult.value = true
    }
  }
)

// 拿到身份牌就自动弹出来。
// immediate 是为了兼容"刷新页面时牌已经发完了"的情况 —— 不然刷新一次就看不到自己的身份。
watch(
  () => game.roleCard,
  (card) => {
    if (card) {
      showRoleCard.value = true
    }
  },
  { immediate: true }
)

/** 对局记录区的 DOM，用来做"新消息自动滚到底" */
const logBox = ref<HTMLElement | null>(null)

/** v-for 出来的每一条记录（用最后一条的 scrollIntoView 兜底） */
const logItems = ref<HTMLElement[]>([])

/** 用户在往上翻历史时，积攒的"下面还有几条新消息" */
const unreadLogs = ref(0)

/** 离底部 120px 以内就算"他正在看最新的"（一条记录约 30px，留点余量） */
function isLogAtBottom(box: HTMLElement) {
  return box.scrollHeight - box.scrollTop - box.clientHeight < 120
}

/** 滚到底（点"下面有新消息"时也用这个） */
function scrollLogsToBottom() {
  const box = logBox.value
  if (box) {
    box.scrollTop = box.scrollHeight
  }
  // 第二重保险：让最后一条自己滚进视野。
  // 交给浏览器算布局时机，比我们手设 scrollTop 可靠得多。
  const items = logItems.value
  if (items && items.length) {
    items[items.length - 1]?.scrollIntoView({ block: 'nearest' })
  }
  unreadLogs.value = 0
}

/** 用户自己滑到底部时，把"新消息"计数清掉 */
function onLogScroll() {
  const box = logBox.value
  if (box && isLogAtBottom(box)) {
    unreadLogs.value = 0
  }
}

/**
 * 有新记录时：人在底部就跟着往下滚；人在往上翻看历史就**不打扰他**，
 * 只在角落挂一个"下面有 N 条新消息"。
 *
 * 两个细节很关键：
 *  1. `flush: 'post'` —— 保证在 DOM 更新**之后**才执行，否则量到的 scrollHeight 还是旧的
 *  2. 再套一层 `requestAnimationFrame` —— nextTick 时浏览器有时还没算完布局，
 *     直接设 scrollTop 会被随后的重排覆盖掉（之前就是这么"看起来没生效"的）
 */
watch(
  () => game.logs.length,
  async (now, before) => {
    await nextTick()
    const box = logBox.value
    if (!box) {
      return
    }
    if (isLogAtBottom(box)) {
      box.scrollTop = box.scrollHeight
      // 再用 scrollIntoView 兜一层：有些情况下 nextTick 时浏览器还没算完布局，
      // 单纯设 scrollTop 会被随后的重排覆盖掉（之前就是这么"看起来没生效"的）
      const items = logItems.value
      if (items && items.length) {
        items[items.length - 1]?.scrollIntoView({ block: 'nearest' })
      }
      requestAnimationFrame(() => {
        const b = logBox.value
        if (b) {
          b.scrollTop = b.scrollHeight
        }
      })
      unreadLogs.value = 0
    } else {
      unreadLogs.value += Math.max(1, now - (before ?? 0))
    }
  },
  { flush: 'post' }
)

// 进页面时日志里可能已经攒了一批（比如刷新过），先滚到底
onMounted(() => {
  nextTick(() => {
    const box = logBox.value
    if (box) {
      box.scrollTop = box.scrollHeight
    }
  })
})

/**
 * 最后一道保险：组件每次更新后都检查一遍。
 *
 * <p>为什么不只靠 watch：watch 盯的是"记录条数变化"这个时机，
 * 而滚动还取决于浏览器有没有算完布局 —— 两者偶尔会错开，
 * 表现就是"明明加了新记录，却停在原地"。</p>
 *
 * <p>{@code onUpdated} 是在 **DOM 真的更新完成之后** 才跑的，这里再补一刀最稳。</p>
 *
 * <p>判断逻辑没变：离底部很近（说明他本来就在看最新消息）才跟着滚；
 * 已经在上面翻历史（差着几百像素）就坚决不打扰他。</p>
 */
onUpdated(() => {
  const box = logBox.value
  if (box && isLogAtBottom(box)) {
    box.scrollTop = box.scrollHeight
  }
})

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

/** 我是不是狼（狼队才看得到队友的选择） */
const isWolf = computed(() => privateState.value?.myRole === 'WOLF')

/**
 * 狼队每个人的目标：座位号 → 目标座位号。
 *
 * 直接用这个在座位卡上给队友标出"刀了 X 号"，
 * 一眼就能看清狼队是不是分票了、要不要改。
 */
const wolfVotesBySeat = computed(() => {
  const votes = (privateState.value?.info?.wolfVotes ?? {}) as Record<string, number>
  const result: Record<number, number> = {}
  for (const [seat, target] of Object.entries(votes)) {
    result[Number(seat)] = target
  }
  return result
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
  if (can('HUNTER_SHOOT')) return '开枪带走'
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
      <!-- 主持人的解说。不用 <Transition>：它在频繁切换时会让旧元素卡住不消失。 -->
      <div v-if="game.hostLine" :key="game.hostLine.text" class="host-line">
        <span class="host-label">🎙 主持人</span>
        <span class="host-text">{{ game.hostLine.text }}</span>
      </div>

      <!-- 夜晚提示：同样是"旧的先走干净、新的再进来"。
           外面套一层 slot 用绝对定位让两条**重叠** —— 否则交接时会把页面顶得跳一下。
           注意 active 这个 class：没有提示条时（比如白天）slot **不能占高度**，
           否则会在页面上方空出一大截（之前就踩了这个坑）。 -->
      <!-- 夜晚行动提示。
           ★ 这里**故意不用 Vue 的 <Transition>**。
             之前用 `<Transition mode="out-in">` + `:key` 做的交叉淡入淡出，在
             "频繁切换 + 需要让旧元素消失"的场景下有坑：动画结束没被识别时，
             元素会**卡住不消失** —— 表现就是"天亮了，预言家正在行动还挂在那儿"。
             现在的做法简单粗暴但绝对可靠：
               · v-if 直接控制存在与否（不出现在 DOM 里就不可能卡住）
               · :key 让换人时重新挂载，CSS 入场动画自然重播
               · 消失不做动画（一瞬间干净消失，反而更利落） -->
      <div class="banner-slot" :class="{ active: !!nightBanner }">
        <div
          v-if="nightBanner"
          :key="nightBanner.cls"
          class="night-banner"
          :class="nightBanner.cls"
        >
          <span class="night-icon">{{ nightBanner.icon }}</span>
          <span class="night-text">{{ nightBanner.text }}</span>
          <span class="night-sub">{{ nightBanner.sub }}</span>
        </div>
      </div>

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
                  <el-tag v-if="seat.ai" size="small" type="success">🤖</el-tag>
                  <el-tag v-if="seat.aiPersonaTag" size="small" effect="plain">
                    {{ seat.aiPersonaTag }}
                  </el-tag>
                  <el-tag v-if="teammates.includes(seat.seatNo)" size="small" type="danger">狼队友</el-tag>
                  <!-- 狼队友刀了谁：狼队要商量这个，所以直接标在座位上 -->
                  <el-tag
                    v-if="isWolf && wolfVotesBySeat[seat.seatNo]"
                    size="small"
                    type="warning"
                    effect="dark"
                  >
                    刀了 {{ wolfVotesBySeat[seat.seatNo] }} 号
                  </el-tag>
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
                </div>
                <!-- 票数单独占一行、做得醒目：
                     投票时一眼就能看出谁快被票出去了，不用在一堆状态标签里找 -->
                <div v-if="seat.voteCount > 0" class="seat-votes">
                  {{ seat.voteCount }} 票
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
            <div ref="logBox" class="logs" @scroll.passive="onLogScroll">
              <div
                v-for="(log, index) in game.logs"
                :key="index"
                ref="logItems"
                class="log"
                :class="log.kind"
              >
                <span class="log-time">{{ log.time }}</span>
                <span class="log-text">{{ log.text }}</span>
              </div>
              <div v-if="game.logs.length === 0" class="log-empty">暂无记录</div>
            </div>
            <!-- 用户在往上翻历史时，别把他拽回来，但得让他知道下面有新东西 -->
            <div v-if="unreadLogs > 0" class="log-new" @click="scrollLogsToBottom">
              ↓ 下面有 {{ unreadLogs }} 条新消息
            </div>
          </el-card>
        </div>
      </div>
    </template>

    <!-- 身份牌弹窗：开局自动弹一次，让每个人清楚自己是谁 -->
    <el-dialog
      v-model="showRoleCard"
      title="你的身份"
      width="400px"
      align-center
      :close-on-click-modal="false"
    >
      <div v-if="game.roleCard" class="role-card">
        <div class="role-emoji">{{ roleIntro.emoji }}</div>
        <div class="role-seat">{{ game.roleCard.seatNo }} 号位</div>
        <div class="role-name">{{ game.roleCard.roleLabel }}</div>
        <div
          class="role-camp"
          :class="game.roleCard.camp === 'WOLF' ? 'camp-wolf' : 'camp-good'"
        >
          {{ game.roleCard.camp === 'WOLF' ? '狼人阵营' : '好人阵营' }}
        </div>
        <div class="role-desc">{{ roleIntro.desc }}</div>
        <div v-if="roleTeammates.length" class="role-teammates">
          🐺 你的狼队友：{{ roleTeammates.join('、') }} 号
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="showRoleCard = false">知道了</el-button>
      </template>
    </el-dialog>

    <!-- 投票出局提示：给所有人弹一下，省得有人没看右侧日志就不知道谁出去了 -->
    <el-dialog
      v-model="showVoteResult"
      title="投票结果"
      width="380px"
      align-center
      :show-close="false"
    >
      <div class="vote-result">
        <div class="vote-emoji">{{ voteResultEmoji }}</div>
        <div class="vote-text">{{ voteResultText }}</div>
        <div v-if="voteResultDetail" class="vote-detail">{{ voteResultDetail }}</div>
      </div>
      <template #footer>
        <el-button type="primary" @click="showVoteResult = false">知道了</el-button>
      </template>
    </el-dialog>

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

        <!-- AI 的复盘：它们会记得自己哪里打错了，下一局带着这个教训上场 -->
        <div v-if="reflectionList.length" class="reflections">
          <div class="reflections-title">🧠 它们自己怎么看这一局</div>
          <div v-for="item in reflectionList" :key="item.seatNo" class="reflection-item">
            <span class="reflection-who">{{ item.seatNo }} 号 {{ item.nickname }}</span>
            <span class="reflection-text">{{ item.text }}</span>
          </div>
        </div>
        <div v-else-if="waitingReflection" class="reflections-waiting">
          🧠 AI 正在复盘这一局…
        </div>
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

/* 投票出局提示 */
.vote-result {
  text-align: center;
  padding: 8px 0;
}

.vote-emoji {
  font-size: 44px;
  line-height: 1;
  margin-bottom: 12px;
}

.vote-text {
  font-size: 17px;
  font-weight: 600;
  color: var(--at-text);
}

.vote-detail {
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.07);
  color: var(--at-text-regular);
  font-size: 13px;
}

/* 开局的身份牌：尽量一眼看懂，不用研究规则 */
.role-card {
  text-align: center;
  padding: 4px 0 8px;
}

.role-emoji {
  font-size: 52px;
  line-height: 1;
  margin-bottom: 10px;
}

.role-seat {
  font-size: 13px;
  color: var(--at-text-secondary);
}

.role-name {
  font-size: 26px;
  font-weight: 700;
  margin: 6px 0 10px;
  letter-spacing: 2px;
}

.role-camp {
  display: inline-block;
  padding: 3px 14px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
}

.role-camp.camp-wolf {
  background: rgba(239, 68, 68, 0.16);
  color: #f87171;
}

.role-camp.camp-good {
  background: rgba(16, 185, 129, 0.16);
  color: #34d399;
}

.role-desc {
  margin-top: 14px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--at-text-regular);
  text-align: left;
  background: rgba(255, 255, 255, 0.07);
  border-radius: 8px;
  padding: 12px 14px;
}

.role-teammates {
  margin-top: 10px;
  font-size: 14px;
  color: #f87171;
  font-weight: 600;
}

/* 结算面板里的 AI 复盘 */
.reflections {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.07);
}

.reflections-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 8px;
  color: var(--at-text-regular);
}

.reflection-item {
  display: flex;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
  line-height: 1.6;
}

.reflection-who {
  flex-shrink: 0;
  color: var(--at-text-secondary);
  min-width: 96px;
}

.reflection-text {
  flex: 1;
  color: var(--at-text);
}

.reflections-waiting {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.07);
  color: var(--at-text-secondary);
  font-size: 13px;
  text-align: center;
}

.back {
  margin-top: 16px;
}

/* 主持人的一句话：比普通日志醒目，但不抢夜晚提示条的位置 */
.host-line {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 16px;
  margin-bottom: 12px;
  border-radius: 8px;
  background: rgba(245, 158, 11, 0.14);
  border-left: 4px solid #fbbf24;
  font-size: 15px;
  line-height: 1.6;
  color: #fcd34d;
}
.host-label {
  flex-shrink: 0;
  font-weight: 600;
}

.host-text {
  flex: 1;
}

/* 夜晚提示的容器：用相对定位让新旧两条重叠，交叉淡入淡出不会顶动页面。
   ★ 只有真的有提示条时才占高度（.active）—— 白天没提示条却留着 52px，
     会在页面上方空出一大截，看着很突兀。 */
.banner-slot {
  position: relative;
}

.banner-slot.active {
  min-height: 52px;
  margin-bottom: 12px;
}

/* 夜晚的行动提示条：一眼能看出现在轮到谁，但不泄露目标 */.night-banner {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  border-radius: 8px;
  border: 1px solid transparent;
  font-size: 16px;
  font-weight: 600;
  /* 两个动画叠着跑：
       ① bannerIn  —— 刚出现时从上方滑入（只跑一次）
       ② nightPulse —— 之后一直轻微呼吸，表示"正在行动中"
     注意 nightPulse 动的是 box-shadow 而不是 opacity，否则会和入场动画打架。
     消失**不做动画**：v-if 直接把它从 DOM 里拿掉，一瞬间干净消失。
     这比 Vue 的 <Transition> 可靠得多 —— 那个在频繁切换时会让元素卡住不消失。 */
  animation:
    bannerIn 0.22s ease-out,
    nightPulse 2.4s ease-in-out 0.22s infinite;
}

@keyframes bannerIn {
  from {
    opacity: 0;
    transform: translateY(-18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes nightPulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(255, 255, 255, 0);
  }
  50% {
    /* 深色底上用白色微光做呼吸，黑色阴影是看不见的 */
    box-shadow: 0 0 0 5px rgba(255, 255, 255, 0.07);
  }
}

.night-banner.wolf {
  background: rgba(239, 68, 68, 0.16);
  color: #f87171;
  border-color: rgba(239, 68, 68, 0.35);
}

.night-banner.witch {
  background: rgba(168, 85, 247, 0.16);
  color: #c4b5fd;
  border-color: rgba(168, 85, 247, 0.35);
}

.night-banner.seer {
  background: rgba(56, 189, 248, 0.16);
  color: #7dd3fc;
  border-color: rgba(56, 189, 248, 0.35);
}

.night-icon {
  font-size: 22px;
  line-height: 1;
}

.night-sub {
  font-size: 13px;
  font-weight: 400;
  opacity: 0.72;
}

@keyframes nightPulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.72;
  }
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.06);
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
  color: var(--at-text);
}

.alive-info {
  color: var(--at-text-secondary);
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
  color: #fbbf24;
  font-size: 13px;
}

.seats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.seat {
  border: 2px solid rgba(255, 255, 255, 0.13);
  border-radius: 8px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.06);
  transition: all 0.15s;
  cursor: default;
}

.seat.selectable {
  cursor: pointer;
  border-color: rgba(129, 140, 248, 0.4);
  background: rgba(255, 255, 255, 0.06);
}

.seat.selectable:hover {
  border-color: #818cf8;
}

.seat.selected {
  border-color: #818cf8;
  background: rgba(129, 140, 248, 0.18);
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.15);
}

.seat.me {
  border-color: #34d399;
}

.seat.teammate {
  border-color: #f87171;
}

.seat.speaking {
  background: rgba(245, 158, 11, 0.14);
  border-color: #fbbf24;
}

.seat.dead {
  opacity: 0.55;
  background: rgba(255, 255, 255, 0.05);
  border-style: dashed;
}

.seat.offline {
  background: rgba(255, 255, 255, 0.05);
  border-style: dotted;
}

.seat-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.seat-no {
  font-size: 12px;
  color: var(--at-text-secondary);
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
  color: #f87171;
  font-weight: bold;
  font-size: 13px;
}

.action-card .hint {
  color: var(--at-text-regular);
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
  color: var(--at-text-secondary);
  font-size: 14px;
}

.private-info {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  color: var(--at-text-regular);
  font-size: 13px;
}

.logs {
  max-height: 520px;
  overflow-y: auto;
}

/* "下面还有 N 条新消息" —— 只在用户往上翻历史时出现，点一下回到底部 */
.log-new {
  margin-top: 8px;
  padding: 4px 0;
  text-align: center;
  font-size: 13px;
  color: #818cf8;
  background: rgba(129, 140, 248, 0.18);
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
}

.log-new:hover {
  background: rgba(129, 140, 248, 0.28);
}

/* 座位上的票数：单独一行、显眼一点，投票时好判断局势 */
.seat-votes {
  margin-top: 6px;
  padding: 2px 0;
  text-align: center;
  font-size: 15px;
  font-weight: 700;
  color: #fbbf24;
  background: rgba(245, 158, 11, 0.14);
  border-radius: 6px;
}

.log {
  display: flex;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed rgba(255, 255, 255, 0.08);
  font-size: 13px;
  line-height: 1.5;
}

.log-time {
  color: var(--at-text-faint);
  flex-shrink: 0;
}

.log.phase .log-text {
  color: #818cf8;
  font-weight: bold;
}

.log.death .log-text {
  color: #f87171;
}

.log.speak .log-text {
  color: var(--at-text);
}

.log.vote .log-text {
  color: #fbbf24;
}

.log-empty {
  color: var(--at-text-faint);
  font-size: 13px;
  text-align: center;
  padding: 20px 0;
}

.muted {
  color: var(--at-text-faint);
}
</style>
