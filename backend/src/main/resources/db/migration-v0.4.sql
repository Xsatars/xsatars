-- ============================================================
--  V0.4 迁移脚本：给战绩表加"复盘"
--
--  只有从 V0.3 升级时才需要执行；全新安装直接用 schema.sql。
--
--  执行方式（在项目根目录）：
--    D:\dev\MySQL\MySQL Server 8.0\bin\mysql.exe -u root -proot < backend\src\main\resources\db\migration-v0.4.sql
-- ============================================================

USE ai_table;

-- AI 对这局的复盘（一句话）。
-- 下一局会作为跨局记忆喂回给同一个 AI —— 所以它能说出"上一局我不该跟票"。
ALTER TABLE game_records
    ADD COLUMN reflection VARCHAR(300) DEFAULT NULL COMMENT 'AI 对这局的复盘';

SELECT 'V0.4 迁移完成' AS 结果;
