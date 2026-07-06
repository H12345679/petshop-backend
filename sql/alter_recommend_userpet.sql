-- ============================================================
-- 推荐深化 P0：宠物档案 + 标签补齐 + 商品自动打标
-- 对应《推荐系统深化方案.md》第 3.2 节与 P0 落地项
-- 执行方式：在 petshop 库上执行本脚本（仅需执行一次）
-- ============================================================

-- 1. 用户宠物档案表
CREATE TABLE IF NOT EXISTS `user_pet` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `name` varchar(50) DEFAULT NULL COMMENT '宠物昵称',
  `species` tinyint NOT NULL COMMENT '种类 1猫咪 2狗狗 3兔子 4鸟类 9其他（对齐tag字典）',
  `breed` varchar(50) DEFAULT NULL COMMENT '品种 如英短/金毛',
  `gender` tinyint DEFAULT NULL COMMENT '1公 2母',
  `birthday` date DEFAULT NULL COMMENT '生日：<1岁幼年 1~7岁成年 >7岁老年',
  `weight_kg` decimal(5,2) DEFAULT NULL COMMENT '体重kg：犬≥15大型 <15小型',
  `sterilized` tinyint DEFAULT NULL COMMENT '是否绝育 0否 1是',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='用户宠物档案';

-- 2. 标签字典补齐（现有 1狗狗 2猫咪 3兔子 4鸟类 5大型犬 6小型犬 7幼年 8成年
--    9短毛 10长毛 11主食 12零食 13玩具 14无谷 15鸡肉 16磨牙 17互动 18户外）
INSERT IGNORE INTO `tag` (`id`, `name`, `create_time`, `deleted`) VALUES
  (19, '老年', NOW(), 0),
  (20, '清洁用品', NOW(), 0),
  (21, '服饰美容', NOW(), 0);

-- 3. 按商品名关键词自动补打标签（幂等：uk_product_tag 唯一键 + INSERT IGNORE）
SET @pt_id = (SELECT COALESCE(MAX(id), 1000) FROM product_tag);

-- 3.1 物种标签
INSERT IGNORE INTO product_tag (id, product_id, tag_id)
SELECT (@pt_id := @pt_id + 1), p.id, 2 FROM product p
WHERE p.deleted = 0 AND p.name LIKE '%猫%'
  AND NOT EXISTS (SELECT 1 FROM product_tag pt WHERE pt.product_id = p.id AND pt.tag_id = 2);

INSERT IGNORE INTO product_tag (id, product_id, tag_id)
SELECT (@pt_id := @pt_id + 1), p.id, 1 FROM product p
WHERE p.deleted = 0 AND (p.name LIKE '%狗%' OR p.name LIKE '%犬%')
  AND NOT EXISTS (SELECT 1 FROM product_tag pt WHERE pt.product_id = p.id AND pt.tag_id = 1);

INSERT IGNORE INTO product_tag (id, product_id, tag_id)
SELECT (@pt_id := @pt_id + 1), p.id, 3 FROM product p
WHERE p.deleted = 0 AND p.name LIKE '%兔%'
  AND NOT EXISTS (SELECT 1 FROM product_tag pt WHERE pt.product_id = p.id AND pt.tag_id = 3);

INSERT IGNORE INTO product_tag (id, product_id, tag_id)
SELECT (@pt_id := @pt_id + 1), p.id, 4 FROM product p
WHERE p.deleted = 0 AND (p.name LIKE '%鸟%' OR p.name LIKE '%鹦鹉%')
  AND NOT EXISTS (SELECT 1 FROM product_tag pt WHERE pt.product_id = p.id AND pt.tag_id = 4);

-- 3.2 品类标签
INSERT IGNORE INTO product_tag (id, product_id, tag_id)
SELECT (@pt_id := @pt_id + 1), p.id, 11 FROM product p
WHERE p.deleted = 0 AND (p.name LIKE '%粮%' OR p.name LIKE '%罐头%' OR p.name LIKE '%主食%')
  AND NOT EXISTS (SELECT 1 FROM product_tag pt WHERE pt.product_id = p.id AND pt.tag_id = 11);

INSERT IGNORE INTO product_tag (id, product_id, tag_id)
SELECT (@pt_id := @pt_id + 1), p.id, 12 FROM product p
WHERE p.deleted = 0 AND (p.name LIKE '%零食%' OR p.name LIKE '%冻干%' OR p.name LIKE '%洁齿%')
  AND NOT EXISTS (SELECT 1 FROM product_tag pt WHERE pt.product_id = p.id AND pt.tag_id = 12);

INSERT IGNORE INTO product_tag (id, product_id, tag_id)
SELECT (@pt_id := @pt_id + 1), p.id, 13 FROM product p
WHERE p.deleted = 0 AND p.name LIKE '%玩具%'
  AND NOT EXISTS (SELECT 1 FROM product_tag pt WHERE pt.product_id = p.id AND pt.tag_id = 13);

INSERT IGNORE INTO product_tag (id, product_id, tag_id)
SELECT (@pt_id := @pt_id + 1), p.id, 20 FROM product p
WHERE p.deleted = 0 AND (p.name LIKE '%猫砂%' OR p.name LIKE '%尿垫%' OR p.name LIKE '%沐浴%' OR p.name LIKE '%清洁%' OR p.name LIKE '%除臭%')
  AND NOT EXISTS (SELECT 1 FROM product_tag pt WHERE pt.product_id = p.id AND pt.tag_id = 20);

-- 3.3 年龄段标签（幼猫/幼犬/成猫/成犬，避免误伤只匹配组合词）
INSERT IGNORE INTO product_tag (id, product_id, tag_id)
SELECT (@pt_id := @pt_id + 1), p.id, 7 FROM product p
WHERE p.deleted = 0 AND (p.name LIKE '%幼猫%' OR p.name LIKE '%幼犬%' OR p.name LIKE '%奶糕%')
  AND NOT EXISTS (SELECT 1 FROM product_tag pt WHERE pt.product_id = p.id AND pt.tag_id = 7);

INSERT IGNORE INTO product_tag (id, product_id, tag_id)
SELECT (@pt_id := @pt_id + 1), p.id, 8 FROM product p
WHERE p.deleted = 0 AND (p.name LIKE '%成猫%' OR p.name LIKE '%成犬%')
  AND NOT EXISTS (SELECT 1 FROM product_tag pt WHERE pt.product_id = p.id AND pt.tag_id = 8);

INSERT IGNORE INTO product_tag (id, product_id, tag_id)
SELECT (@pt_id := @pt_id + 1), p.id, 19 FROM product p
WHERE p.deleted = 0 AND (p.name LIKE '%老年%' OR p.name LIKE '%老犬%' OR p.name LIKE '%老猫%')
  AND NOT EXISTS (SELECT 1 FROM product_tag pt WHERE pt.product_id = p.id AND pt.tag_id = 19);
