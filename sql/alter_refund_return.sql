-- ============================================================
-- 退单流程增强：仅退款/退货退款、未收到货(快递退款)、退货物流单号
-- 对应老师验收意见（2026-07）：
--   1. 已发货用户未收到货(快递退回) → 仅退款，商家"确认退货退款"
--   2. 已收到货仅退款：申请需体现是否收到货
--   3. 退货退款：审核通过后用户填退货快递单号，商家确认收货后打款
--   4. 已评价(状态4)仍可退款，但必须退货退款，商家审核有特别提示
-- 执行方式：在 petshop 库上执行本脚本（仅需执行一次）
-- ============================================================

ALTER TABLE `refund`
  ADD COLUMN `refund_type` tinyint NOT NULL DEFAULT '1' COMMENT '1仅退款 2退货退款' AFTER `type`,
  ADD COLUMN `received` tinyint NOT NULL DEFAULT '1' COMMENT '申请时是否已收到货 0未收到(快递退款) 1已收到' AFTER `refund_type`,
  ADD COLUMN `description` varchar(500) DEFAULT NULL COMMENT '问题描述' AFTER `reason`,
  ADD COLUMN `images` varchar(2000) DEFAULT NULL COMMENT '凭证图片(JSON数组)' AFTER `description`,
  ADD COLUMN `return_courier_company` varchar(50) DEFAULT NULL COMMENT '退货物流公司' AFTER `audit_remark`,
  ADD COLUMN `return_tracking_number` varchar(50) DEFAULT NULL COMMENT '退货物流单号' AFTER `return_courier_company`,
  ADD COLUMN `return_time` datetime DEFAULT NULL COMMENT '用户寄回时间' AFTER `return_tracking_number`,
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT '0' COMMENT '0申请中 1已退款(结束) 2已驳回 3待用户退货 4待商家确认收货';
