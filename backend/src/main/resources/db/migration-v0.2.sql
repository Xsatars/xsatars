-- ============================================================
--  V0.2 迁移脚本：把已有的 V0.1 数据库升级上来
--
--  只有从 V0.1 升级时才需要执行这个；
--  全新安装直接用 schema.sql 就行（里面已经包含这些字段）。
--
--  执行方式（在项目根目录）：
--    D:\dev\MySQL\MySQL Server 8.0\bin\mysql.exe -u root -proot < backend\src\main\resources\db\migration-v0.2.sql
-- ============================================================

USE ai_table;

-- AI 玩家没有真实账号，user_id 允许为空
-- （MySQL 的 UNIQUE 索引允许多个 NULL，所以一个房间可以放多个 AI）
ALTER TABLE players
    MODIFY COLUMN user_id BIGINT UNSIGNED DEFAULT NULL COMMENT '用户ID（AI 玩家为 NULL）';

-- 标记这个席位是不是 AI
ALTER TABLE players
    ADD COLUMN is_ai TINYINT NOT NULL DEFAULT 0 COMMENT '是否 AI 玩家 1=AI';

-- AI 的名字（真人玩家用 users.nickname，AI 没有 user 记录）
ALTER TABLE players
    ADD COLUMN ai_name VARCHAR(32) DEFAULT NULL COMMENT 'AI 名字（真人用 users.nickname）';

-- 战绩表也要能记 AI：user_id 允许为空 + 记下 AI 的名字
ALTER TABLE game_records
    MODIFY COLUMN user_id BIGINT UNSIGNED DEFAULT NULL COMMENT '用户ID（AI 玩家为 NULL）';

ALTER TABLE game_records
    ADD COLUMN ai_name VARCHAR(32) DEFAULT NULL COMMENT 'AI 名字（真人用 users.nickname）';

SELECT 'V0.2 迁移完成' AS 结果;
