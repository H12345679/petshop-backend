-- 支持按订单明细粒度退款（部分退款）
-- 执行前请备份数据库

-- 1. 退单表增加关联订单明细ID（NULL = 整单退款，向下兼容）
ALTER TABLE refund ADD COLUMN order_item_id BIGINT NULL COMMENT '关联订单明细ID(NULL=整单退款)' AFTER order_id;

-- 2. 订单明细表增加退款状态 0正常 1退款中 2已退款
ALTER TABLE order_item ADD COLUMN refund_status TINYINT NOT NULL DEFAULT 0 COMMENT '退款状态: 0正常 1退款中 2已退款';

-- 3. 将已有退款中/已退款订单的所有明细同步标记
UPDATE order_item oi
  INNER JOIN orders o ON o.id = oi.order_id
SET oi.refund_status = CASE
  WHEN o.status IN (-3, -4) THEN 2
  WHEN o.status = -2 THEN 1
  ELSE 0
END
WHERE o.status IN (-2, -3, -4);
