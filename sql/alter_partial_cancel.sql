-- 部分取消：为 order_item 添加取消状态字段
ALTER TABLE order_item ADD COLUMN cancel_status TINYINT NOT NULL DEFAULT 0 COMMENT '取消状态: 0正常 1已取消';
