-- 仅用于新环境建库；本机已确认存在的新项目库无需重复初始化。
-- 只创建sky_takeout_ai，不操作旧库，不在本脚本创建业务表。
CREATE DATABASE IF NOT EXISTS sky_takeout_ai
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
