-- ============================================================
-- 修复：address 表补充 longitude / latitude 两列
-- 背景：Address 实体已含经纬度字段（commit ecaf459 为地址增加维度和经度），
--       但旧库由 petshop.sql 建表时未含这两列，导致
--       "Unknown column 'longitude' in 'field list'" —— 地址查询全部 500，
--       进而结算页金额显示 ¥0.00、下单校验地址失败。
-- 用法：mysql -h127.0.0.1 -P3307 -uroot -p petshop < sql/alter-address-add-lnglat.sql
-- 说明：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，此脚本仅需执行一次；
--       若列已存在会报 "Duplicate column name"，忽略即可。
-- ============================================================

ALTER TABLE `address`
    ADD COLUMN `longitude` DECIMAL(10, 6) NULL COMMENT '经度' AFTER `detail`,
    ADD COLUMN `latitude`  DECIMAL(10, 6) NULL COMMENT '纬度' AFTER `longitude`;
