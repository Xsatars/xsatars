<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  closeRoom as closeRoomApi,
  getGameDetail,
  getOverview,
  listGames,
  listRooms,
  listUsers,
  updateUserStatus
} from '@/api/admin'
import type { AdminGameDetailVO, AdminGameVO, AdminOverviewVO, AdminRoomVO, AdminUserVO } from '@/types'

const router = useRouter()
const auth = useAuthStore()

const activeTab = ref('overview')
const overview = ref<AdminOverviewVO | null>(null)

// ---------- 用户管理 ----------
const users = ref<AdminUserVO[]>([])
const userLoading = ref(false)
const userQuery = reactive({ page: 1, size: 10, total: 0, keyword: '', status: undefined as number | undefined })

// ---------- 房间管理 ----------
const rooms = ref<AdminRoomVO[]>([])
const roomLoading = ref(false)
const roomQuery = reactive({ page: 1, size: 10, total: 0, status: '' })

// ---------- 游戏记录 ----------
const games = ref<AdminGameVO[]>([])
const gameLoading = ref(false)
const gameQuery = reactive({ page: 1, size: 10, total: 0 })
const detail = ref<AdminGameDetailVO | null>(null)
const detailVisible = ref(false)

onMounted(async () => {
  await auth.restore()
  if (auth.user?.role !== 'ADMIN') {
    ElMessage.error('没有管理员权限')
    router.push('/')
    return
  }
  await loadOverview()
})

async function loadOverview() {
  const res = await getOverview()
  overview.value = res.data
}

// ---------- 刷新 ----------

const autoRefresh = ref(false)
const refreshing = ref(false)
let refreshTimer: number | undefined

/** 把当前这个标签页的数据重新拉一遍 */
async function refreshCurrentTab() {
  if (activeTab.value === 'overview') {
    await loadOverview()
  } else if (activeTab.value === 'users') {
    await loadUsers()
  } else if (activeTab.value === 'rooms') {
    await loadRooms()
  } else if (activeTab.value === 'games') {
    await loadGames()
  }
}

/** 手动刷新（顶上那个按钮） */
async function refreshAll() {
  refreshing.value = true
  try {
    await refreshCurrentTab()
    ElMessage.success('已刷新')
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    refreshing.value = false
  }
}

/** 打开自动刷新后，每 10 秒重新拉一次当前页的数据 */
function handleAutoRefreshChange(value: boolean) {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = undefined
  }
  if (value) {
    refreshTimer = window.setInterval(() => {
      refreshCurrentTab().catch(() => {
        // 自动刷新失败就静默跳过，下次再试
      })
    }, 10000)
    ElMessage.info('已开启自动刷新（每 10 秒一次）')
  }
}

onUnmounted(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})

// ---------- ① 概览 ----------

function handleTabChange(name: string | number) {
  if (name === 'users' && users.value.length === 0) {
    loadUsers()
  } else if (name === 'rooms' && rooms.value.length === 0) {
    loadRooms()
  } else if (name === 'games' && games.value.length === 0) {
    loadGames()
  }
}

// ---------- ② 用户管理 ----------

async function loadUsers() {
  userLoading.value = true
  try {
    const res = await listUsers({
      page: userQuery.page,
      size: userQuery.size,
      keyword: userQuery.keyword || undefined,
      status: userQuery.status
    })
    users.value = res.data.records
    userQuery.total = res.data.total
  } catch (e) {
    // 拦截器已提示
  } finally {
    userLoading.value = false
  }
}

function searchUsers() {
  userQuery.page = 1
  loadUsers()
}

async function toggleUserStatus(row: AdminUserVO) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 0 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}用户「${row.nickname}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await updateUserStatus(row.id, next)
    ElMessage.success(`已${action}`)
    await loadUsers()
  } catch (e) {
    // 拦截器已提示
  }
}

// ---------- ③ 房间管理 ----------

async function loadRooms() {
  roomLoading.value = true
  try {
    const res = await listRooms({
      page: roomQuery.page,
      size: roomQuery.size,
      status: roomQuery.status || undefined
    })
    rooms.value = res.data.records
    roomQuery.total = res.data.total
  } catch (e) {
    // 拦截器已提示
  } finally {
    roomLoading.value = false
  }
}

function searchRooms() {
  roomQuery.page = 1
  loadRooms()
}

async function handleCloseRoom(row: AdminRoomVO) {
  try {
    await ElMessageBox.confirm(`确定要强制关闭房间「${row.roomNo}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '关闭房间',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await closeRoomApi(row.id)
    ElMessage.success('房间已关闭')
    await loadRooms()
  } catch (e) {
    // 拦截器已提示
  }
}

// ---------- ④ 游戏记录 ----------

async function loadGames() {
  gameLoading.value = true
  try {
    const res = await listGames({ page: gameQuery.page, size: gameQuery.size })
    games.value = res.data.records
    gameQuery.total = res.data.total
  } catch (e) {
    // 拦截器已提示
  } finally {
    gameLoading.value = false
  }
}

async function showDetail(row: AdminGameVO) {
  try {
    const res = await getGameDetail(row.id)
    detail.value = res.data
    detailVisible.value = true
  } catch (e) {
    // 拦截器已提示
  }
}

// ---------- 显示辅助 ----------

const roomStatusText = (status: string) =>
  ({ WAITING: '等待中', PLAYING: '进行中', FINISHED: '已结束' })[status] ?? status

const roomStatusType = (status: string) => {
  if (status === 'WAITING') return 'success'
  if (status === 'PLAYING') return 'warning'
  return 'info'
}

const campText = (camp?: string) => (camp === 'WOLF' ? '狼人阵营' : camp === 'GOOD' ? '好人阵营' : '-')

const roleText = (role: string) =>
  ({ WOLF: '狼人', SEER: '预言家', WITCH: '女巫', VILLAGER: '平民' })[role] ?? role
</script>

<template>
  <div class="admin">
    <el-card class="panel">
      <div class="head">
        <div>
          <h1 class="title">管理后台</h1>
          <p class="subtitle">当前登录：{{ auth.user?.nickname }}（管理员）</p>
        </div>
        <div class="head-actions">
          <el-switch
            v-model="autoRefresh"
            active-text="自动刷新"
            @change="handleAutoRefreshChange"
          />
          <el-button :loading="refreshing" @click="refreshAll">刷新</el-button>
          <el-button @click="router.push('/')">返回首页</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- ============ ① 数据概览 ============ -->
        <el-tab-pane label="数据概览" name="overview">
          <div v-if="overview">
            <el-row :gutter="12" class="stat-row">
              <el-col :span="6">
                <div class="stat">
                  <div class="stat-label">用户总数</div>
                  <div class="stat-value">{{ overview.userCount }}</div>
                  <div class="stat-sub">今日新增 {{ overview.todayNewUsers }}</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="stat">
                  <div class="stat-label">在线连接</div>
                  <div class="stat-value">{{ overview.onlineCount }}</div>
                  <div class="stat-sub">WebSocket 连接数</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="stat">
                  <div class="stat-label">房间总数</div>
                  <div class="stat-value">{{ overview.roomCount }}</div>
                  <div class="stat-sub">
                    等待 {{ overview.waitingRoomCount }} · 进行中 {{ overview.playingRoomCount }} · 结束
                    {{ overview.finishedRoomCount }}
                  </div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="stat">
                  <div class="stat-label">对局总数</div>
                  <div class="stat-value">{{ overview.gameCount }}</div>
                  <div class="stat-sub">已结束 {{ overview.finishedGameCount }}</div>
                </div>
              </el-col>
            </el-row>

            <el-divider>运行状态</el-divider>

            <el-descriptions :column="2" border>
              <el-descriptions-item label="MySQL">
                <el-tag :type="overview.system.mysqlConnected ? 'success' : 'danger'" size="small">
                  {{ overview.system.mysqlConnected ? '正常' : '异常' }}
                </el-tag>
                <span class="detail">
                  版本 {{ overview.system.mysqlVersion || '-' }}，库 {{ overview.system.mysqlDatabase || '-' }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="Redis">
                <el-tag :type="overview.system.redisConnected ? 'success' : 'danger'" size="small">
                  {{ overview.system.redisConnected ? '正常' : '异常' }}
                </el-tag>
                <span class="detail">版本 {{ overview.system.redisVersion || '-' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="Java 版本">{{ overview.system.javaVersion }}</el-descriptions-item>
              <el-descriptions-item label="统计时间">{{ overview.generatedAt }}</el-descriptions-item>
            </el-descriptions>

            <div class="refresh">
              <el-button size="small" @click="loadOverview">刷新</el-button>
            </div>
          </div>
          <el-skeleton v-else :rows="4" animated />
        </el-tab-pane>

        <!-- ============ ② 用户管理 ============ -->
        <el-tab-pane label="用户管理" name="users">
          <div class="toolbar">
            <el-input
              v-model="userQuery.keyword"
              placeholder="搜索用户名或昵称"
              class="search"
              clearable
              @keyup.enter="searchUsers"
            />
            <el-select v-model="userQuery.status" placeholder="全部状态" clearable class="status-select" @change="searchUsers">
              <el-option label="正常" :value="1" />
              <el-option label="已禁用" :value="0" />
            </el-select>
            <el-button type="primary" @click="searchUsers">搜索</el-button>
          </div>

          <el-table v-loading="userLoading" :data="users" border stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="username" label="用户名" min-width="140" />
            <el-table-column prop="nickname" label="昵称" min-width="120" />
            <el-table-column label="角色" width="100">
              <template #default="{ row }">
                <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'" size="small">
                  {{ row.role === 'ADMIN' ? '管理员' : '玩家' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ row.status === 1 ? '正常' : '已禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="注册时间" width="170" />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button
                  link
                  :type="row.status === 1 ? 'danger' : 'primary'"
                  @click="toggleUserStatus(row)"
                >
                  {{ row.status === 1 ? '禁用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            class="pager"
            layout="total, prev, pager, next"
            :total="userQuery.total"
            :page-size="userQuery.size"
            :current-page="userQuery.page"
            @current-change="(p: number) => { userQuery.page = p; loadUsers() }"
          />
        </el-tab-pane>

        <!-- ============ ③ 房间管理 ============ -->
        <el-tab-pane label="房间管理" name="rooms">
          <div class="toolbar">
            <el-select v-model="roomQuery.status" placeholder="全部状态" clearable class="status-select" @change="searchRooms">
              <el-option label="等待中" value="WAITING" />
              <el-option label="进行中" value="PLAYING" />
              <el-option label="已结束" value="FINISHED" />
            </el-select>
            <el-button type="primary" @click="searchRooms">筛选</el-button>
          </div>

          <el-table v-loading="roomLoading" :data="rooms" border stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="roomNo" label="房间号" width="110" />
            <el-table-column prop="name" label="房间名" min-width="160" />
            <el-table-column prop="ownerNickname" label="房主" width="120" />
            <el-table-column label="人数" width="90">
              <template #default="{ row }">{{ row.playerCount }} / {{ row.maxPlayers }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="roomStatusType(row.status)" size="small">{{ roomStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="170" />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button
                  link
                  type="danger"
                  :disabled="row.status === 'FINISHED'"
                  @click="handleCloseRoom(row)"
                >
                  关闭
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            class="pager"
            layout="total, prev, pager, next"
            :total="roomQuery.total"
            :page-size="roomQuery.size"
            :current-page="roomQuery.page"
            @current-change="(p: number) => { roomQuery.page = p; loadRooms() }"
          />
        </el-tab-pane>

        <!-- ============ ④ 游戏记录 ============ -->
        <el-tab-pane label="游戏记录" name="games">
          <el-alert
            class="hint"
            type="info"
            :closable="false"
            show-icon
            title="对局记录会在游戏引擎实现后产生"
            description="现在列表是空的，属正常情况——等 V0.1 的 Step 5-6（角色分配、夜晚、投票、胜负判定）做完，每局结束都会在这里留下记录。"
          />

          <el-table v-loading="gameLoading" :data="games" border stripe empty-text="暂无对局记录">
            <el-table-column prop="id" label="对局ID" width="90" />
            <el-table-column prop="roomNo" label="房间号" width="110" />
            <el-table-column prop="boardCode" label="板子" width="140" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'FINISHED' ? 'info' : 'warning'" size="small">
                  {{ row.status === 'FINISHED' ? '已结束' : '进行中' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="胜方" width="110">
              <template #default="{ row }">{{ campText(row.winnerCamp) }}</template>
            </el-table-column>
            <el-table-column prop="totalRounds" label="轮数" width="80" />
            <el-table-column prop="playerCount" label="人数" width="80" />
            <el-table-column prop="startedAt" label="开始时间" width="170" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="showDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            class="pager"
            layout="total, prev, pager, next"
            :total="gameQuery.total"
            :page-size="gameQuery.size"
            :current-page="gameQuery.page"
            @current-change="(p: number) => { gameQuery.page = p; loadGames() }"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 对局详情弹窗 -->
    <el-dialog v-model="detailVisible" title="对局详情" width="720px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="对局ID">{{ detail.game.id }}</el-descriptions-item>
          <el-descriptions-item label="房间号">{{ detail.game.roomNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="胜方">{{ campText(detail.game.winnerCamp) }}</el-descriptions-item>
          <el-descriptions-item label="轮数">{{ detail.game.totalRounds }}</el-descriptions-item>
          <el-descriptions-item label="开始">{{ detail.game.startedAt }}</el-descriptions-item>
          <el-descriptions-item label="结束">{{ detail.game.endedAt || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-table class="detail-table" :data="detail.players" border size="small">
          <el-table-column prop="seatNo" label="座位" width="70" />
          <el-table-column prop="nickname" label="玩家" min-width="120" />
          <el-table-column label="角色" width="100">
            <template #default="{ row }">{{ roleText(row.roleCode) }}</template>
          </el-table-column>
          <el-table-column label="阵营" width="110">
            <template #default="{ row }">{{ campText(row.camp) }}</template>
          </el-table-column>
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.result === 'WIN' ? 'success' : 'info'" size="small">
                {{ row.result === 'WIN' ? '胜' : '负' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="存活" width="90">
            <template #default="{ row }">{{ row.survived === 1 ? '存活' : '死亡' }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin {
  display: flex;
  justify-content: center;
  padding: 30px 16px;
}

.panel {
  width: 100%;
  max-width: 1100px;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.title {
  margin: 0 0 4px;
  font-size: 22px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.subtitle {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.stat-row {
  margin-bottom: 8px;
}

.stat {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  background: #fafcff;
}

.stat-label {
  color: #909399;
  font-size: 13px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
  margin: 6px 0;
}

.stat-sub {
  color: #909399;
  font-size: 12px;
}

.detail {
  margin-left: 8px;
  color: #606266;
  font-size: 13px;
}

.refresh {
  margin-top: 14px;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.search {
  max-width: 260px;
}

.status-select {
  max-width: 150px;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.hint {
  margin-bottom: 12px;
}

.detail-table {
  margin-top: 14px;
}
</style>
