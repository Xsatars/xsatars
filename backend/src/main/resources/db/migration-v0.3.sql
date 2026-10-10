-- ============================================================
--  V0.3 迁移脚本：给 AI 加"性格"
--
--  只有从 V0.2 升级时才需要执行；全新安装直接用 schema.sql。
--
--  执行方式（在项目根目录）：
--    D:\dev\MySQL\MySQL Server 8.0\bin\mysql.exe -u root -proot < backend\src\main\resources\db\migration-v0.3.sql
-- ============================================================

USE ai_table;

-- AI 的性格标识（对应 AiPersona 枚举的 name，比如 HOTHEAD / SNEAKY）
ALTER TABLE players
    ADD COLUMN ai_persona VARCHAR(32) DEFAULT NULL COMMENT 'AI 性格标识（AiPersona 枚举名）';

-- 战绩里也留一份，方便以后统计"哪种性格胜率高"
ALTER TABLE game_records
    ADD COLUMN ai_persona VARCHAR(32) DEFAULT NULL COMMENT 'AI 性格标识';

SELECT 'V0.3 迁移完成' AS 结果;
