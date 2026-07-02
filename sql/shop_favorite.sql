CREATE TABLE IF NOT EXISTS shop_favorite (
  id bigint NOT NULL COMMENT '主键',
  user_id bigint NOT NULL COMMENT '用户id',
  shop_id bigint NOT NULL COMMENT '门店id',
  create_time datetime DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  PRIMARY KEY (id) USING BTREE,
  UNIQUE KEY uk_user_shop (user_id,shop_id) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='店铺关注表';
