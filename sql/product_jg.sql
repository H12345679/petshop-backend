/*
 Navicat Premium Dump SQL

 Source Server         : docker_mysql_3307
 Source Server Type    : MySQL
 Source Server Version : 80029 (8.0.29)
 Source Host           : localhost:3307
 Source Schema         : petshop

 Target Server Type    : MySQL
 Target Server Version : 80029 (8.0.29)
 File Encoding         : 65001

 Date: 02/07/2026 17:47:46
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for product
-- ----------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product`  (
  `id` bigint NOT NULL COMMENT '主键',
  `shop_id` bigint NOT NULL COMMENT '所属商店id',
  `category_id` bigint NULL DEFAULT NULL COMMENT '分类id',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品名称',
  `type` tinyint NOT NULL DEFAULT 2 COMMENT '1宠物(唯一,库存=1) 2周边(数量不限)',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '商品详情',
  `price` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '售价(无规格时用)',
  `original_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '原价',
  `stock` int NOT NULL DEFAULT 0 COMMENT '库存(无规格时用;宠物=1)',
  `sales` int NOT NULL DEFAULT 0 COMMENT '销量',
  `main_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '主图',
  `images` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '多图(JSON数组)',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_shop`(`shop_id` ASC) USING BTREE,
  INDEX `idx_category`(`category_id` ASC) USING BTREE,
  INDEX `idx_type`(`type` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_status_time`(`status` ASC, `create_time` DESC) USING BTREE,
  INDEX `idx_status_sales`(`sales` DESC, `status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
