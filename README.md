# AI Table

多人在线 AI 桌游平台。第一款游戏是**狼人杀**，未来会扩展剧本杀、AI RPG、德州扑克等。

> A multiplayer online board-game platform. The first game is **Werewolf** (6 players).
> Runs with a single command — Docker is the only prerequisite.

---

## 🚀 快速开始（Docker，推荐）

**只需要装 Docker**，MySQL、Redis、应用本体全在容器里，数据库表首次启动自动创建。

```bash
git clone <仓库地址>
cd ai-table
docker compose up -d
```

等一二十秒（首次构建镜像会久一点），浏览器打开 **http://localhost:8080** 就能玩。

> **Windows 用户注意**：Docker Desktop 需要 **WSL2** 才能运行。
> 如果启动 Docker 时提示 `Docker Desktop is unable to start`，通常是 WSL 没装好：
> **以管理员身份**打开 PowerShell，执行 `wsl --install`，然后**重启电脑**。
> （Docker Desktop 安装时也要用管理员权限，否则它既开不了 WSL、也注册不了自己的服务。）

| 想做什么 | 怎么做 |
| --- | --- |
| 一个人先试试 | 注册 6 个账号，开 6 个浏览器窗口轮流操作 |
| 和朋友一起玩（同一 WiFi） | 把 `http://你的局域网IP:8080` 发给他们 |
| 和朋友一起玩（异地） | 用内网穿透，见「想让别人试玩」一章 |
| 停掉服务 | `docker compose down`（数据保留） |
| 连数据一起清掉 | `docker compose down -v` |
| 改数据库密码 | 改 `docker-compose.yml` 里的 `DB_PASSWORD` 和 `MYSQL_ROOT_PASSWORD`（两处要一致） |

### Quick start (English)

```bash
git clone <repo-url>
cd ai-table
docker compose up -d
# then open http://localhost:8080
```

The only requirement is Docker. MySQL, Redis and the application all run in containers,
and the database schema is created automatically on first start.

---

> 当前进度：**V0.1 前端后端全部完成**
> 能真正玩完整一局：注册登录 → 建房 → 多人加入 → 房主准备 → 发身份牌 → 夜晚（狼人 → 女巫 → 预言家依次行动）
> → 白天发言（含遗言）→ 投票放逐 → 胜负判定 → 结算面板 → 战绩落库。

---

## 一、目录结构

```text
ai-table/
├── start-backend.cmd            一键启动后端（自动设置 JDK 21）
├── start-frontend.cmd           一键启动前端
├── README.md
│
├── backend/                     后端（Spring Boot 3.3.13 + Java 21）
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/aitable/
│       │   ├── AiTableApplication.java   启动类
│       │   ├── common/                   统一返回 Result、业务异常、全局异常处理
│       │   ├── config/                   跨域、MyBatis-Plus、Redis、WebSocket 配置
│       │   ├── controller/               REST 接口（目前只有健康检查）
│       │   ├── service/ + service/impl/  业务逻辑（目前只有健康检查）
│       │   ├── mapper/                   MyBatis-Plus Mapper（空，占位）
│       │   ├── entity/                   数据库实体（空，占位）
│       │   ├── dto/                      请求参数对象（空，占位）
│       │   ├── vo/                       返回给前端的数据对象
│       │   ├── websocket/                WebSocket 消息处理（一个连通性测试）
│       │   └── game/                     游戏引擎（空，占位，后续放 core 与 werewolf）
│       └── resources/
│           ├── application.yml           配置（数据库、Redis、MyBatis-Plus）
│           └── mapper/                   MyBatis XML 目录（空，占位）
│
└── frontend/                    前端（Vue3 + TypeScript + Vite + Element Plus）
    ├── package.json
    ├── vite.config.ts            开发服务器 + /api、/ws 代理到后端 8080
    ├── tsconfig.json
    ├── index.html
    └── src/
        ├── main.ts               入口：注册 Pinia、Router、Element Plus
        ├── App.vue               根组件
        ├── api/                  request.ts（axios + token 拦截器）、auth.ts、room.ts、health.ts
        ├── assets/               静态资源与全局样式
        ├── components/           可复用组件（空，占位）
        ├── router/               路由：/login、/register、/ 大厅、/room/:roomId、/game/:gameId、/admin
        ├── stores/               auth.ts（登录）、room.ts（房间）、game.ts（对局）
        ├── types/                TypeScript 类型（与后端 DTO 一一对应）
        ├── utils/                工具函数
        ├── views/                LoginView、RegisterView、HomeView（大厅）、
        │                          RoomView（房间页）、GameView（游戏页）、AdminView（管理后台）
        └── ws/                   client.ts：STOMP 客户端封装（自动重连、订阅管理）
```

---

## 二、运行环境要求

| 组件 | 版本要求 | 备注 |
| --- | --- | --- |
| JDK | **21**（最低 17） | 后端用 Java 21 编译运行 |
| Maven | 3.9+ | 构建后端 |
| MySQL | 8.0+ | 字符集必须 utf8mb4；建表脚本在 `backend/src/main/resources/db/schema.sql` |
| Redis | 6+ | 存登录 token、房间闲置计时 |
| Node.js | 18+（推荐 20） | 只在需要**构建前端页面**时用到 |

> **不想一样一样装？用 Docker 最省事**：`docker compose up -d`（见上面「快速开始」）。
> MySQL / Redis / 应用全在容器里，数据库表也会自动建好。

数据库连接配置在 `backend/src/main/resources/application.yml`，并且支持用**环境变量覆盖**：

```
DB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORD
REDIS_HOST / REDIS_PORT
```

不设置就用默认值（`localhost` / `root` / `root`），也就是本机开发那一套——
所以本地跑不用改任何配置，Docker 里则由 `docker-compose.yml` 传进来。

---

## 三、如何启动

### 方式一：Docker（推荐，最省事）

```bash
docker compose up -d
```

然后打开 <http://localhost:8080>。MySQL、Redis、应用全在容器里，数据库表自动创建。

| 操作 | 命令 |
| --- | --- |
| 看日志 | `docker compose logs -f app` |
| 停掉（数据保留） | `docker compose down` |
| 连数据一起清掉 | `docker compose down -v` |
| 重新构建（改了代码） | `docker compose up -d --build` |

### 方式二：本地跑（改代码、调试时用）

需要先装好上一章列的那几个东西。

**第 1 步：启动 MySQL 和 Redis**

- MySQL：确认服务在运行（Windows 上通常是 `net start MYSQL80`）
- Redis：用你自己的方式启动（Windows 可以下载
  [Redis for Windows](https://github.com/tporadowski/redis/releases)，解压后跑 `redis-server.exe`）

**第 2 步：建库建表**

```sql
CREATE DATABASE ai_table DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

然后执行 `backend/src/main/resources/db/schema.sql`（建 5 张表）。

**第 3 步：启动后端和前端**

Windows 上直接双击就行（脚本会自动找 JDK 和 Maven，找不到会明确提示你改哪里）：

```
start-backend.cmd      启动后端（8080）
start-frontend.cmd     启动前端（5173，改代码会热更新）
```

或者手动敲命令：

```bash
# 后端
cd backend
mvn spring-boot:run

# 前端（另开一个终端）
cd frontend
npm install      # 第一次需要
npm run dev
```

然后浏览器打开 <http://localhost:5173>。

> **两个端口别打架**：`start-standalone.cmd`（打包版，前后端都在 8080）
> 和 `start-backend.cmd`（开发版）**都要用 8080**，不能同时开。

---

## 四、验证结果（2026-10-07 实测）

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 后端编译打包 | `mvn clean package` | ✅ **BUILD SUCCESS**，耗时 2 分 08 秒，产物 `backend/target/ai-table-backend-0.0.1-SNAPSHOT.jar` |
| 后端启动 | `java -jar ...` | ✅ 9.9 秒启动完成，Tomcat 运行在 8080，Spring Boot 3.3.13 / Java 21.0.12.1 |
| MySQL 连接 | `GET /api/health` | ✅ `mysqlConnected: true`，版本 8.0.46，库 `ai_table` |
| Redis 连接 | `GET /api/health` | ✅ `redisConnected: true`，版本 8.10.2（真实写入+读取验证） |
| 整体状态 | `GET /api/health` | ✅ `status: "UP"` |
| 简单接口 | `GET /api/ping` | ✅ 返回 `pong` |
| 前端安装依赖 | `npm install` | ✅ 104 个包安装成功 |
| 前端类型检查 | `npm run type-check` | ✅ 无类型错误 |
| 前端启动 | `npm run dev` | ✅ Vite 运行在 5173，页面正常返回 |
| 前端生产构建 | `npm run build` | ✅ 1681 个模块转换成功，耗时 9.14 秒，生成 `dist/` |
| **前后端连通** | 浏览器 → `5173/api/health` → 后端 8080 | ✅ 代理转发成功，返回 `status: UP` |
| WebSocket 连通 | `ws://localhost:8080/ws` | ✅ STOMP 握手成功，`/app/ping` → `/topic/pong` 消息往返正常 |
| **V0.1-S2** 建表 | `mvn test` | ✅ 5 张表建好，Mapper 读写与唯一约束测试通过 |
| **V0.1-S3** 用户与房间 | `mvn test` | ✅ **14 个测试全通过**（登录自动注册、密码加密存储、满员拒绝、房主转移等） |
| **V0.1-S3** 接口实测 | 真启动服务调 HTTP | ✅ **25 项检查全通过**（登录/鉴权 401/建房/加入/幂等/查询/离开） |
| **V0.1-S4** WebSocket | 真启动服务连 WS | ✅ **17 项检查全通过**（连接鉴权拒绝无 token/无效 token、加入离开实时广播、错误处理、在线状态） |
| **前端** 首页 + 房间页 | 类型检查 + 构建 + 联调 | ✅ 类型检查无错误、构建成功、**21 项联调检查全通过**（6 人凑齐、实时座位推送、满员拒绝、房主转交） |
| **管理后台** | 端到端调接口 | ✅ **33 项检查全通过**（角色识别、403 权限拦截、四个模块、禁用用户、关闭房间） |
| **登录/注册拆分** | 端到端调接口 | ✅ **27 项检查全通过**（注册校验、重复用户名、账号不存在提示、老账号不受影响） |
| **游戏引擎** | `mvn test` | ✅ **20 个引擎测试全通过**（发牌、信息隔离、夜晚流程、毒药解药、发言顺序、投票、平票、两种胜负条件） |
| **完整对局** | 6 个 WebSocket 客户端自动打完一局 | ✅ **26 项检查全通过**（发牌 → 夜晚 → 白天发言 → 投票 → 胜负 → 战绩落库） |
| **超时机制** | `mvn test` + 6 人挂机实测 | ✅ **13 个超时测试 + 7 项挂机实测全通过**（空刀 / 跳过发言 / 弃权 / 平局兜底） |
| **前端游戏页** | 类型检查 + 构建 + 接口实测 | ✅ 类型检查无错误、构建成功、**22 项检查全通过**（模块编译、视图接口、身份隔离、公告文字、状态刷新） |
| **掉线处理与重连** | 端到端实测 | ✅ **17 项检查全通过**（在线状态、掉线 0.4 秒内自动跳过、重连接口、管理员关闭对局同步结束） |
| **准备功能** | `mvn test` + 端到端实测 | ✅ **3 个新单元测试 + 16 项检查全通过**（房主免准备、未准备不能开局、取消准备、后端真拦截） |
| **遗言 / 弃权 / 倒计时 / 单页面限制** | `mvn test` + 端到端实测 | ✅ **6 个新单元测试 + 13 项检查全通过**（遗言、发言与投票弃权、平票空刀、票数清理、不泄露行动、倒计时、账号顶替） |
| **狼队协商 / 房间状态** | `mvn test` + 端到端实测 | ✅ **1 个新单元测试 + 10 项检查全通过**（狼人可见狼队刀人、好人看不到、房主立即在线、一局结束后全部重新准备） |
| **新功能批**（闲置清理 / 个人主页 / 找回密码 / 踢人 / 头像 / 管理刷新） | `mvn test` + 端到端实测 | ✅ **29 项检查全通过** |
| **夜晚分步进行** | `mvn test` + 端到端实测 | ✅ **1 个新单元测试 + 14 项检查全通过**（狼→女巫→预言家依次行动、没轮到的抢跑被拒、公告不重复） |

---

## 五、接口一览

### 通用（不需要登录）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/ping` | 连通性测试，返回 `pong` |
| GET | `/api/health` | 后端、MySQL、Redis 的状态 |

### 用户

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/users/register` | **注册**：用户名 3-50 位（字母/数字/下划线）、密码 6-32 位、昵称必填。返回 `{ token, user, newUser }` |
| POST | `/api/users/login` | **登录**：账号不存在会提示「账号不存在，请先注册」（**不会**自动建账号） |
| GET | `/api/users/me` | 查询当前登录用户（前端刷新后恢复登录态） |

### 房间

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/rooms` | 创建房间（创建者自动成为房主并坐 1 号位） |
| POST | `/api/rooms/{roomId}/join` | 加入房间；`{roomId}` 可以传 **6 位房间号**或房间ID |
| GET | `/api/rooms/{roomId}` | 查询房间详情（含座位列表、是否可以开局） |
| DELETE | `/api/rooms/{roomId}/players/{playerId}` | 离开房间（只能让自己离开；房主离开会自动转交房主） |
| POST | `/api/rooms/{roomId}/players/{playerId}/kick` | **房主踢人**（只有房主能用，且只能踢别人） |
| POST | `/api/rooms/{roomId}/ready` | **准备 / 取消准备**（`{ "ready": true }`）；房主不需要准备，调了会被拒绝 |
| POST | `/api/rooms/{roomId}/start` | **房主开始游戏**：**满员 + 除房主外全部已准备**才能开局，自动发身份牌并进入第一夜。返回新对局 ID |

### 对局

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/games/{gameId}` | 查询我在这局里的视图（公共状态 + 我的私人信息与可用动作），刷新/重连时用 |

### 管理后台（**只有管理员能访问**，其它人返回 403）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/overview` | ① 数据概览：用户数、房间数、对局数、在线数 + MySQL/Redis 状态 |
| GET | `/api/admin/users` | ② 用户列表（支持 `keyword` 搜索、`status` 筛选、分页） |
| PUT | `/api/admin/users/{userId}/status` | ② 启用 / 禁用用户（不能禁用自己） |
| GET | `/api/admin/rooms` | ③ 房间列表（支持 `status` 筛选、分页） |
| POST | `/api/admin/rooms/{roomId}/close` | ③ 强制关闭房间 |
| GET | `/api/admin/games` | ④ 对局记录列表（分页） |
| GET | `/api/admin/games/{gameId}` | ④ 对局详情（每人角色与胜负） |

**谁是管理员**：`application.yml` 里的 `ai-table.admin-usernames`（默认 `admin`）。
用名单里的用户名登录，账号会**自动获得管理员角色**。想让自己已有的账号成为管理员，
把用户名加进这个配置（多个用逗号分隔）再重启后端即可。
前端入口：登录后在首页底部会看到「进入管理后台」（普通玩家看不到这个入口）。

**网页端**：<http://localhost:5173/admin>

**登录方式**：除 `/api/users/login`、`/api/users/register`、`/api/health`、`/api/ping` 外，
所有 `/api/**` 都要带请求头 `Authorization: Bearer {token}`，否则返回 401。
token 有效期 24 小时，存在 Redis 里。

**为什么登录和注册分开**：如果"账号不存在就自动注册"，用户名打错一个字（`alice` → `alic`）
就会悄悄多出一个账号，用户会以为自己的房间丢了。分开之后语义清晰，注册页还能单独做
密码确认、长度校验这些规则。

### WebSocket（实时通信）

| 项目 | 内容 |
| --- | --- |
| 端点 | `ws://localhost:8080/ws?token={登录拿到的token}`（STOMP 协议） |
| 鉴权 | **握手时校验 token**，没带或无效直接拒绝连接 |
| 房间公共频道 | 客户端订阅 `/topic/room/{roomId}`（座位变化等公开信息） |
| 个人私有频道 | 客户端订阅 `/user/queue/game`（自己的角色、查验结果等） |
| 客户端发消息 | 统一发到 `/app/action` |

消息统一格式：

```json
// 客户端 → 服务端
{ "type": "JOIN_ROOM",  "data": { "roomNo": "123456" } }
{ "type": "LEAVE_ROOM", "data": { "roomId": 1, "playerId": 2 } }

// 服务端 → 客户端
{ "type": "ROOM_UPDATE", "data": { ...房间信息... }, "timestamp": 1791352266769 }
{ "type": "ERROR", "message": "房间已满" }
```

**已实现**：`JOIN_ROOM`、`LEAVE_ROOM`、`START_GAME`、`WEREWOLF_KILL`、`SEER_CHECK`、`WITCH_SAVE`、
`WITCH_POISON`、`VOTE`、`SPEAK`、`PASS`（客户端→服务端）；`ROOM_UPDATE`、`GAME_START`、`ROLE_ASSIGN`、
`PHASE_CHANGE`、`NIGHT_RESULT`、`PLAYER_DEAD`、`PLAYER_SPEAK`、`VOTE_UPDATE`、`VOTE_RESULT`、
`GAME_STATE_UPDATE`、`GAME_END`、`ERROR`（服务端→客户端）。

**信息隔离**：`ROLE_ASSIGN`（身份牌）、`NIGHT_RESULT`（查验结果、谁被刀）这类私有消息**只发给对应的人**；
`GAME_STATE_UPDATE` 是每人一份（含他自己的可用动作）。房间频道 `/topic/room/{id}` 上永远只有公开信息。

进入房间后，其他玩家加入/离开会**自动收到 `ROOM_UPDATE`**，前端不需要轮询。

### 返回格式

所有 REST 接口统一返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { },
  "timestamp": 1791352266769
}
```

前端 `api/request.ts` 里的拦截器统一判断 `code === 200` 并弹出错误提示，
页面代码不需要每次写 try/catch。

---

## 六、当前**没有**实现的功能

**已完成**：基础架构 · 数据库 5 张表 · 注册/登录 · 房间系统 · WebSocket 实时同步 ·
**狼人杀完整玩法**（发牌、夜晚三技能、发言、投票、死亡、胜负判定、超时兜底、战绩落库）·
**前端六个页面**（登录、注册、大厅、房间、游戏、管理后台）

**还没做**（属于后续版本）：

- AI 玩家、AI Agent、LLM、Tool Calling、Memory、AI 主持人（V0.2 起）
- 多实例部署（现在对局状态在单机内存里，服务重启会丢失正在进行的对局）
- 观战、对局回放、非发言阶段的自由聊天

---

## 七、技术要点说明

**开多个窗口分别登录不同账号，为什么不会互相覆盖？**
同一个浏览器的多个普通窗口、多个无痕窗口是**共享 localStorage** 的。
一开始 token 只存在 localStorage，结果是：你在 A 窗口登录 player1、再去 B 窗口登录 player2 之后，
A 窗口发出去的请求其实带的是 player2 的身份 —— 于是出现「我明明是房主，却提示我不是房主」这种怪事。
现在 token **同时写一份到 sessionStorage**（每个标签页独立），各窗口互不干扰；
新开一个标签页时又会自动沿用上次登录的账号。

**玩家掉线（关页面 / 断网 / 直接走人）会怎样？**
- 房间里和游戏桌上，他的座位立刻显示「离线」
- 对局里**立刻替他跳过当前该他做的操作**（狼人空刀、预言家跳过查验、女巫视为不用药、发言记为跳过、投票弃权），
  不让全桌干等 90 秒超时
- 重新打开页面时，首页会出现「你有一局还没打完的对局」+「回到对局」按钮
- 重新连上后恢复「在线」显示

**玩家挂机（人还在、就是不操作）会怎样？**
每一步（夜晚行动 / 发言 / 投票）都有超时，默认 90 秒，到点自动跳过。配置项：

```yaml
ai-table:
  game:
    action-timeout-seconds: 90       # 每步最多等多少秒（设 0 = 关掉超时）
    timeout-check-interval-ms: 2000  # 多久扫一次
```

另外还有兜底：如果所有人都挂机（一直空刀 + 没人出局），到 **20 轮**会判平局收场，不会无限循环占着服务器。

**怎么才算「可以开局」？**
两个条件都要满足：**人满（6 人）** + **除房主外所有人都点了「准备」**。
房主不需要准备（他点「开始游戏」），创建房间时就视为已就绪。
前端把按钮置灰只是提示，**后端也会拦**：人数不够或有人没准备，房主强行调开局接口会被拒绝，
并返回「还有 N 人没有准备」。有人中途「取消准备」，开局按钮会立刻变回不可用。

**狼队之间能看到什么？**
狼人能看到队友是谁，以及**狼队各自刀了谁**（界面上显示成「你 → 3 号　｜　2 号 → 5 号」这样的协商视图），
以及已经定下来的「今晚目标」。好人完全看不到这些信息（接口层面就不下发）。

**一局打完后回到房间，准备状态会重置吗？**
会。对局结束后房间自动回到等待状态，除房主外所有人的准备状态被重置为「未准备」，
这样上一局的准备状态不会被当成这一局的。

**夜晚是怎么进行的？**
按 **狼人 → 女巫 → 预言家** 依次进行，不是同时进行：

1. 先是**狼人**选今晚刀谁（狼队能看到彼此的选择，方便商量）
2. 狼队定了才轮到**女巫**——她必须先知道「今晚谁被刀」才能决定救不救，所以只能排在狼人后面
3. 最后是**预言家**查验
4. 三步都做完才天亮

没轮到的人，界面上**一个操作按钮都没有**；如果绕过界面抢跑，服务端会拒绝并提示「现在还没轮到 X 行动」。
每一步各自计时（各自 90 秒），所以挂机时不会一个人拖住全桌。

**狼人刀人不一致怎么办？**
两只狼各自刀不同的人时算「意见不统一」，**今晚空刀**（谁都不死），并公告「狼人意见不统一，今晚没有刀人」。

**夜里出局的人还能说话吗？**
能，而且排在第二天发言顺序的**最前面**说遗言；说完才轮到存活玩家。
遗言也是一次发言，期间同样可以「放弃发言」。

**发言和投票都能弃权吗？**
能。发言阶段点「放弃发言」会记为「（放弃发言）」并轮到下一个人；
投票阶段点「弃权」不计票，但算「已表态」——否则永远凑不齐"所有人都投完"，对局会卡在投票阶段。

**界面上会不会泄露不该看到的信息？**
不会。座位上的「已行动」标记**只在投票阶段**出现（表示这个人投过票了），夜晚一律不显示——
否则能从"谁已经行动了"推断出狼刀没刀人、女巫用没用解药。
票数同理：进入下一轮时连票数带「已投票」标记一起清空，不会残留到第二天。

**同一个账号能在两个页面一起玩吗？**
不能。同一个账号在两个页面登录时，**后来者顶替先来者**：旧页面会提示「你的账号已在其他页面登录」并自动退回登录页。
每个页面打开时生成一个随机标识（存在 sessionStorage），连 WebSocket 时通过 STOMP 头带上，服务端靠它区分页面。

**房间没人了会自动关掉吗？**
会。房间里所有人都离线（没有任何 WebSocket 连接）超过一段时间（默认 **60 秒**）后，
这个房间会被自动**删除**（连座位一起），把资源释放出来——避免玩家随手建一堆房间然后走人。
只要有人回到房间，计时会立刻清零，不会误删。
只处理「等待中」的房间，正在进行的对局不受影响（对局有自己的超时机制）。

配置：

```yaml
ai-table:
  room:
    idle-close-seconds: 60        # 全员离线多久后清理
    idle-check-interval-ms: 30000 # 多久扫一次
```

**头像存在哪里？没设置会怎样？**
头像存在服务器磁盘上（默认 `./uploads/avatar/`，可用 `ai-table.upload.dir` 改），数据库里只存一个地址。
**没设置头像时显示的是灰色默认头像**（带昵称首字），不是裂图。
限制 2MB、只允许 png / jpg / gif / webp，换头像时旧文件会自动删掉。

**忘记密码怎么验证身份？**
用「用户名 + 注册时填的昵称」——两者都对上才能重设密码。
这是**没有邮箱 / 短信服务时的简化做法**，安全性不如验证码；
账号不存在和昵称对不上会返回**同一句提示**，避免被人拿来试探哪些账号存在。
正式上线前建议换成邮箱或手机验证码。

**操作有没有时间提示？**
有。顶部会显示「⏱ 剩余 N 秒」，快到时间（≤10 秒且需要你操作）时会标红。这个时间就是超时时间，到点会按规则自动跳过。

**管理员在后台强制关房间时会怎样？**
房间和里面正在进行的对局会**一起结束**（对局记录也标记为已结束并写入结束时间），
局里的玩家会收到「对局已被管理员终止」的提示。

**为什么前端请求 `/api/...` 而不是 `http://localhost:8080/api/...`？**
`vite.config.ts` 里配了代理，开发时 `/api` 和 `/ws` 都会转发到后端 8080。
这样前端代码不用关心后端地址，也不会有跨域问题。
后端同时配置了 CORS（`CorsConfig`），直接用 Postman 之类的工具打后端也没问题。

**为什么 `Result` 要包一层 `code/message/data`？**
这样前端只判断一个 `code` 字段就能知道成败，错误提示统一在拦截器里处理。

**为什么现在一行 AI 代码都没有？**
按照项目原则：**AI 负责决策，Game Engine 负责规则**。
必须先有游戏引擎和规则校验，AI 才有可以调用的入口，所以 AI 放到 V0.2 之后。

**构建时提示 `index-*.js` 超过 500KB 怎么办？**
因为现在 Element Plus 是整体引入的。后面做游戏界面时改成按需引入即可，
现在不用管它，只是警告不是错误。

---

## 八、版本记录

每一版都留了一份源码副本放在 `versions/` 下（内容和开发目录 `ai-table/` 完全一致，改哪边都行，记得同步）：

| 版本 | 内容 | 副本位置 |
| --- | --- | --- |
| v0.0-baseline | 空架子：后端能启动、前端能打开、数据库表建好 | `versions/v0.0-baseline/` |
| **v0.1** ✅ | **多人狼人杀 MVP（当前版本）**：完整玩法 + 前端六个页面 + 管理后台 + 66 个单元测试 | `versions/v0.1/` |

**V0.1 的验收方式**：6 个客户端自动打完一整局 —— 注册 → 登录 → 建房 → 6 人加入 → 全部准备 → 开局 →
发身份牌 → 3 轮对局（夜晚/发言/投票）→ 分出胜负 → 战绩落库 → 房间回到等待 → 重新准备再开一局，
**34 项检查全通过**。

每个版本目录下都有一份 `版本说明.md`，写清了这一版能做什么、怎么跑起来、和上一版的区别。

---

## 九、想让别人试玩，但不想给源码

### 做法：打包成一个 jar，别人只要一个网址

```cmd
双击 build-release.cmd       打包（前端页面会被一起塞进 jar 里）
双击 start-standalone.cmd    启动，窗口里会打印出局域网地址
```

启动后窗口里会显示：

```
本机访问：   http://localhost:8080

别人要玩，把下面这个"局域网地址"发给他：
             http://192.168.x.x:8080
```

把那个地址发过去，对方用浏览器打开就能玩（能注册、能建房、能开局），**你不需要给他任何文件**。

### 对方在哪，就怎么给

| 情况 | 怎么做 |
| --- | --- |
| **同一个 WiFi / 局域网**（同事、室友） | 直接用上面打印的局域网地址。第一次启动时 Windows 可能弹防火墙提示，要点「允许访问」 |
| **异地**（外地的朋友） | 用内网穿透工具，见下 |
| 只想让他看效果、连运行都不需要 | 录屏发过去 |

### 异地怎么让对方访问（内网穿透）

**工具已经装好了**：`tools/cloudflared.exe`（Cloudflare 官方的免费穿透工具，不用注册账号、不用备案）。

```cmd
第 1 步  双击 start-standalone.cmd    先把游戏服务跑起来（这个窗口别关）
第 2 步  双击 start-tunnel.cmd        再开穿透
第 3 步  等十几秒，窗口里会出现一行网址，长这样：
             https://xxxx-xxxx-xxxx.trycloudflare.com
第 4 步  把这个 https 开头的网址发给对方 —— 他在任何地方都能玩
```

关掉穿透窗口 = **网址立刻失效**，别人就打不开了。每次启动网址都不一样，要发最新的那个。

> 想用别的工具也行，原理一样：把本机 8080 暴露到公网。比如 ngrok：`ngrok http 8080`。
>
> 实测过：穿透之后页面、接口、**WebSocket（wss）**都能正常工作，
> 6 个人隔着公网开局、收身份牌、实时同步都没有问题。

### 关于"不给源码"这件事，说清楚

| 对方能拿到什么 | 能不能还原出源码 |
| --- | --- |
| **只有一个网址**（推荐） | 基本不可能。浏览器里只能看到压缩混淆后的 JS（一个约 1MB、只有 100 来行的文件），后端代码在服务器上根本拿不到 |
| 你发过去的 **jar 文件** | 前端同样是压缩产物；但 **Java 的 jar 可以被反编译**（用 jd-gui 这类工具能还原出大致代码） |

所以：**不想给源码就只给网址，别把 jar 文件本身发出去**。
jar 是给你自己部署用的（比如放到一台云服务器上长期跑，那样即使电脑关了对方也能玩）。

### 打包版和开发版的区别

| | 开发版（`start-backend.cmd` + `start-frontend.cmd`） | 打包版（`start-standalone.cmd`） |
| --- | --- | --- |
| 端口 | 后端 8080 + 前端 5173 | **只有 8080**（前端由后端一起发） |
| 改代码后 | 自动热更新 | 要重新 `build-release.cmd` |
| 用途 | 自己开发调试 | **给别人玩 / 部署** |


