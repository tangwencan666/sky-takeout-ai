-- 执行前先 SHOW DATABASES，确认目标 sky_takeout_ai 不存在；若已存在则暂停并核对归属。
-- 只创建独立的新项目数据库，不操作旧库 sky_take_out，不创建任何业务表。
CREATE DATABASE IF NOT EXISTS sky_takeout_ai
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
