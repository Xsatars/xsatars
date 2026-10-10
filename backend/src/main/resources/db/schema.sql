-- ============================================================
-- AI Table 数据库建表脚本（全新安装用这一份）
-- 说明：包含全部 5 张表，以及各版本迭代中新增的字段
--   users        用户
--   rooms        房间
--   players      房间内的玩家席位
--   games        一局游戏（对局主记录）
--   game_records 每个玩家在一局里的结果
--
-- 用法（会自动建库，不需要提前建）：
--   mysql -u root -p < schema.sql
--
-- 注意：对局进行中的实时状态（角色、存活、阶段、投票）放在内存里，
--       不落 MySQL；一局结束后才把结果写进 games / game_records。
--
-- 从旧版本升级的，不要用这份，用 db/migration-v0.x.sql。
-- ============================================================

-- 建库 + 选中它。
-- ★ 这两行很重要：没有它们，直接 `mysql < schema.sql` 会报
--   "No database selected"（真实踩过，别人部署第一步就卡住）。
CREATE DATABASE IF NOT EXISTS ai_table
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE ai_table;

-- ------------------------------------------------------------
-- 用户
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username    VARCHAR(50)     NOT NULL                COMMENT '登录名',
    password    VARCHAR(100)    NOT NULL                COMMENT '密码（BCrypt 加密后的值）',
    nickname    VARCHAR(50)     NOT NULL                COMMENT '昵称（显示用）',
    avatar      VARCHAR(255)    DEFAULT NULL            COMMENT '头像标识',
    status      TINYINT         NOT NULL DEFAULT 1      COMMENT '1=正常 0=禁用',
    role        VARCHAR(16)     NOT NULL DEFAULT 'USER' COMMENT '角色：USER=普通用户 ADMIN=管理员',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户表';

-- ------------------------------------------------------------
-- 房间
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rooms (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '房间ID',
    room_no         VARCHAR(10)     NOT NULL                COMMENT '房间号（给玩家输入用）',
    name            VARCHAR(100)    DEFAULT NULL            COMMENT '房间名',
    owner_user_id   BIGINT UNSIGNED NOT NULL                COMMENT '房主用户ID',
    game_type       VARCHAR(32)     NOT NULL DEFAULT 'WEREWOLF' COMMENT '游戏类型，后续扩展其它桌游',
    board_code      VARCHAR(32)     NOT NULL DEFAULT 'WEREWOLF_6' COMMENT '板子：WEREWOLF_6（6人局）/ WEREWOLF_8（8人局）',
    max_players     INT             NOT NULL DEFAULT 6      COMMENT '最大玩家数（由板子决定）',
    ai_brain        VARCHAR(16)     DEFAULT 'rule'          COMMENT 'AI 脑子：rule 规则 / llm 大模型（房主可切换）',
    status          VARCHAR(16)     NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING / PLAYING / FINISHED',
    current_game_id BIGINT UNSIGNED DEFAULT NULL            COMMENT '当前对局ID（进行中时有值）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rooms_room_no (room_no),
    KEY idx_rooms_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '房间表';

-- ------------------------------------------------------------
-- 房间内的玩家席位
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS players (
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '席位ID',
    room_id   BIGINT UNSIGNED NOT NULL                COMMENT '房间ID',
    -- AI 玩家没有真实账号，这里为 NULL（MySQL 的 UNIQUE 索引允许多个 NULL，
    -- 所以一个房间可以放多个 AI，不同房间的 AI 也互不干扰）
    user_id   BIGINT UNSIGNED DEFAULT NULL            COMMENT '用户ID（AI 玩家为 NULL）',
    seat_no   INT             NOT NULL                COMMENT '座位号 1..6',
    ready     TINYINT         NOT NULL DEFAULT 0      COMMENT '是否准备 1=已准备',
    is_ai     TINYINT         NOT NULL DEFAULT 0      COMMENT '是否 AI 玩家 1=AI',
    ai_name   VARCHAR(32)     DEFAULT NULL            COMMENT 'AI 名字（真人用 users.nickname）',
    ai_persona VARCHAR(32)    DEFAULT NULL            COMMENT 'AI 性格标识（AiPersona 枚举名）',
    joined_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    left_at   DATETIME        DEFAULT NULL            COMMENT '离开时间（未离开为 NULL）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_players_room_seat (room_id, seat_no),
    UNIQUE KEY uk_players_room_user (room_id, user_id),
    KEY idx_players_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '房间玩家席位表';

-- ------------------------------------------------------------
-- 一局游戏（对局主记录）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS games (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '对局ID',
    room_id      BIGINT UNSIGNED NOT NULL                COMMENT '房间ID',
    game_type    VARCHAR(32)     NOT NULL DEFAULT 'WEREWOLF' COMMENT '游戏类型',
    board_code   VARCHAR(32)     NOT NULL DEFAULT 'WEREWOLF_6' COMMENT '板子',
    status       VARCHAR(16)     NOT NULL DEFAULT 'PLAYING' COMMENT 'PLAYING / FINISHED',
    total_rounds INT             NOT NULL DEFAULT 0      COMMENT '经过的轮数',
    winner_camp  VARCHAR(16)     DEFAULT NULL            COMMENT 'WOLF=狼人胜 / GOOD=好人胜',
    started_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    ended_at     DATETIME        DEFAULT NULL            COMMENT '结束时间',
    PRIMARY KEY (id),
    KEY idx_games_room (room_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '对局表';

-- ------------------------------------------------------------
-- 每个玩家在一局里的结果（游戏结束后写入）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS game_records (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    game_id    BIGINT UNSIGNED NOT NULL                COMMENT '对局ID',
    user_id    BIGINT UNSIGNED DEFAULT NULL            COMMENT '用户ID（AI 玩家为 NULL）',
    ai_name    VARCHAR(32)     DEFAULT NULL            COMMENT 'AI 名字（真人用 users.nickname）',
    ai_persona VARCHAR(32)     DEFAULT NULL            COMMENT 'AI 性格标识',
    reflection VARCHAR(300)    DEFAULT NULL            COMMENT 'AI 对这局的复盘（下一局当跨局记忆）',
    seat_no    INT             NOT NULL                COMMENT '座位号',
    role_code  VARCHAR(16)     NOT NULL                COMMENT '角色：WOLF/SEER/WITCH/VILLAGER',
    camp       VARCHAR(16)     NOT NULL                COMMENT '阵营：WOLF/GOOD',
    result     VARCHAR(8)      NOT NULL                COMMENT 'WIN/LOSE',
    survived   TINYINT         NOT NULL DEFAULT 1      COMMENT '结束时是否存活',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间',
    PRIMARY KEY (id),
    KEY idx_game_records_game (game_id),
    KEY idx_game_records_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '对局玩家战绩表';
