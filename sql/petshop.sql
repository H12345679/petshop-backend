/*
 Navicat Premium Dump SQL

 Source Server         : petshop
 Source Server Type    : MySQL
 Source Server Version : 80029 (8.0.29)
 Source Host           : localhost:3307
 Source Schema         : petshop

 Target Server Type    : MySQL
 Target Server Version : 80029 (8.0.29)
 File Encoding         : 65001

 Date: 29/06/2026 15:36:36
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for address
-- ----------------------------
DROP TABLE IF EXISTS `address`;
CREATE TABLE `address`  (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `receiver` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '收货人',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '收货电话',
  `province` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '省',
  `city` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '市',
  `district` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '区/县',
  `detail` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '详细地址',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '1默认 0非默认',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '收货地址' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of address
-- ----------------------------

-- ----------------------------
-- Table structure for ai_chat_log
-- ----------------------------
DROP TABLE IF EXISTS `ai_chat_log`;
CREATE TABLE `ai_chat_log`  (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户id',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '会话id',
  `question` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提问',
  `answer` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '回答',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI问答记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_chat_log
-- ----------------------------

-- ----------------------------
-- Table structure for cart_item
-- ----------------------------
DROP TABLE IF EXISTS `cart_item`;
CREATE TABLE `cart_item`  (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `sku_id` bigint NOT NULL DEFAULT 0 COMMENT '规格id 0=无规格',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
  `selected` tinyint NOT NULL DEFAULT 1 COMMENT '是否勾选结算 1是 0否',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_product_sku`(`user_id` ASC, `product_id` ASC, `sku_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '购物车项(物理删除)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of cart_item
-- ----------------------------

-- ----------------------------
-- Table structure for coupon
-- ----------------------------
DROP TABLE IF EXISTS `coupon`;
CREATE TABLE `coupon`  (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '券名',
  `type` tinyint NOT NULL DEFAULT 1 COMMENT '1满减 2折扣',
  `threshold` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '满X元可用',
  `amount` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '减Y元 或 折扣(0.9=9折)',
  `total` int NOT NULL DEFAULT 0 COMMENT '发行总量',
  `remain` int NOT NULL DEFAULT 0 COMMENT '剩余数量',
  `start_time` datetime NULL DEFAULT NULL COMMENT '生效时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '失效时间',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1有效 0停用',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '优惠券' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of coupon
-- ----------------------------

-- ----------------------------
-- Table structure for demo_item
-- ----------------------------
DROP TABLE IF EXISTS `demo_item`;
CREATE TABLE `demo_item`  (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '框架自检演示表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of demo_item
-- ----------------------------

-- ----------------------------
-- Table structure for favorite
-- ----------------------------
DROP TABLE IF EXISTS `favorite`;
CREATE TABLE `favorite`  (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_product`(`user_id` ASC, `product_id` ASC) USING BTREE,
  INDEX `idx_product`(`product_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品收藏(物理删除)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of favorite
-- ----------------------------

-- ----------------------------
-- Table structure for membership_level
-- ----------------------------
DROP TABLE IF EXISTS `membership_level`;
CREATE TABLE `membership_level`  (
  `id` bigint NOT NULL COMMENT '主键',
  `level` int NOT NULL COMMENT '等级序号 越大越高',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '等级名 如 普通/银卡/金卡',
  `discount` decimal(3, 2) NOT NULL DEFAULT 1.00 COMMENT '折扣 1=无折扣 0.90=9折',
  `threshold` int NOT NULL DEFAULT 0 COMMENT '升级所需积分',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '等级图标',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权益说明',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会员等级' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of membership_level
-- ----------------------------
INSERT INTO `membership_level` VALUES (1, 1, '普通会员', 1.00, 0, NULL, '无折扣', '2026-06-29 11:40:07', '2026-06-29 11:40:07', 0);
INSERT INTO `membership_level` VALUES (2, 2, '银卡会员', 0.95, 1000, NULL, '95折', '2026-06-29 11:40:07', '2026-06-29 11:40:07', 0);
INSERT INTO `membership_level` VALUES (3, 3, '金卡会员', 0.90, 5000, NULL, '9折', '2026-06-29 11:40:07', '2026-06-29 11:40:07', 0);

-- ----------------------------
-- Table structure for message
-- ----------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message`  (
  `id` bigint NOT NULL COMMENT '主键',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标题',
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '内容',
  `type` tinyint NOT NULL DEFAULT 1 COMMENT '1系统 2订单 3活动 4宠物资讯',
  `scope` tinyint NOT NULL DEFAULT 1 COMMENT '1全体广播 2定向',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '消息内容' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of message
-- ----------------------------

-- ----------------------------
-- Table structure for order_item
-- ----------------------------
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item`  (
  `id` bigint NOT NULL COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `sku_id` bigint NOT NULL DEFAULT 0 COMMENT '规格id',
  `shop_id` bigint NULL DEFAULT NULL COMMENT '商店id(与订单同店)',
  `product_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '商品名(快照)',
  `product_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '商品图(快照)',
  `spec_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '规格(快照)',
  `price` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '单价(快照)',
  `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
  `subtotal` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '小计(price*quantity)',
  `real_pay_amount` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '分摊优惠后实付金额(退款上限)',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order`(`order_id` ASC) USING BTREE,
  INDEX `idx_product`(`product_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单明细' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of order_item
-- ----------------------------

-- ----------------------------
-- Table structure for order_status_log
-- ----------------------------
DROP TABLE IF EXISTS `order_status_log`;
CREATE TABLE `order_status_log`  (
  `id` bigint NOT NULL COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `from_status` tinyint NULL DEFAULT NULL COMMENT '原状态',
  `to_status` tinyint NOT NULL COMMENT '新状态',
  `operator_id` bigint NULL DEFAULT NULL COMMENT '操作人id',
  `operator_role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作人角色 USER/ADMIN',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注/原因',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order`(`order_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单状态流转记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of order_status_log
-- ----------------------------

-- ----------------------------
-- Table structure for orders
-- ----------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders`  (
  `id` bigint NOT NULL COMMENT '主键',
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单号',
  `user_id` bigint NOT NULL COMMENT '下单用户id',
  `shop_id` bigint NOT NULL COMMENT '商店id(一个订单只属于一个商店)',
  `total_amount` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '商品总额',
  `discount_amount` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额(会员折扣+券)',
  `pay_amount` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '实付金额',
  `coupon_id` bigint NOT NULL DEFAULT 0 COMMENT '使用的用户券id(user_coupon.id) 0=未用',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态机: 0待支付 1待发货 2待收货 3待评价 4已完成 -1已取消 -2退款申请中 -3已退款 -4管理员退款',
  `prev_status` tinyint NULL DEFAULT NULL COMMENT '申请退款前的状态(用于退款被拒后恢复)',
  `pay_type` tinyint NULL DEFAULT NULL COMMENT '支付方式 1余额 2模拟支付',
  `pay_time` datetime NULL DEFAULT NULL COMMENT '支付时间',
  `ship_time` datetime NULL DEFAULT NULL COMMENT '发货时间',
  `receive_time` datetime NULL DEFAULT NULL COMMENT '收货时间',
  `finish_time` datetime NULL DEFAULT NULL COMMENT '完成时间',
  `cancel_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '取消原因',
  `receiver_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '收货人(快照)',
  `receiver_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '收货电话(快照)',
  `receiver_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '收货地址(快照)',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_no`(`order_no` ASC) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_shop`(`shop_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of orders
-- ----------------------------

-- ----------------------------
-- Table structure for payment
-- ----------------------------
DROP TABLE IF EXISTS `payment`;
CREATE TABLE `payment`  (
  `id` bigint NOT NULL COMMENT '主键',
  `payment_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '支付流水号',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `amount` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '支付金额',
  `pay_type` tinyint NOT NULL DEFAULT 2 COMMENT '1余额 2模拟支付',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0待支付 1成功 2失败',
  `pay_time` datetime NULL DEFAULT NULL COMMENT '支付时间',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_payment_no`(`payment_no` ASC) USING BTREE,
  INDEX `idx_order`(`order_id` ASC) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付流水' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of payment
-- ----------------------------

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
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of product
-- ----------------------------
INSERT INTO `product` VALUES (1001, 101, 1, '金毛寻回犬幼犬（公）', 1, '3个月大，已打疫苗驱虫，性格温顺亲人，附血统证明', 2800.00, 3200.00, 1, 6, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/007cac9f07e643e3b341a4058ee1c72a.png?e=1814253956&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:F6a5Dfgx5AO2cyWo_TqDmuswfCc=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:26:00', 0);
INSERT INTO `product` VALUES (1002, 101, 1, '英国短毛猫·蓝猫（母）', 1, '4个月，包子脸，疫苗齐全，可上门看猫', 1800.00, 2200.00, 1, 9, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/f355dd8143ac47b6aff9142bf41923f9.png?e=1814254091&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:g3XnN4RLxuSJUXneiyPMCATiL9U=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:28:14', 0);
INSERT INTO `product` VALUES (1003, 101, 1, '布偶猫·海双（母）', 1, 'CFA血统，眼睛蓝，毛量足，附绝育保障', 5800.00, 6500.00, 1, 3, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/3889bfc0a9b747c5bcc0739270c4bb39.png?e=1814254081&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:A2-bTyenFrCsMbj1hC3wGM4w2AA=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:28:03', 0);
INSERT INTO `product` VALUES (1004, 101, 1, '柯基幼犬·三色（公）', 1, '短腿大屁股，活泼好动，已做基础免疫', 3500.00, 3900.00, 1, 5, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/70c8b2b008914f318e653545c9b7424d.png?e=1814254072&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:aLyiHkGqDOzWx_yfgzUvAyz6Z9M=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:27:55', 0);
INSERT INTO `product` VALUES (1005, 101, 1, '荷兰侏儒兔', 1, '成年体重 1kg 左右，温顺易养，附兔粮试用装', 280.00, 360.00, 1, 14, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/4da6e4c118f547f8a82308facf648871.png?e=1814254063&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:pVSXt2G3i8kDNo5uIuHmE_9T0fU=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:27:46', 0);
INSERT INTO `product` VALUES (1006, 101, 1, '玄凤鹦鹉·珍珠', 1, '手养亲人，可上手，含证书与饲养手册', 360.00, 420.00, 1, 8, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/225694613f874bcf8e3aeb2bfd7ee79f.png?e=1814254050&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:h5APtR7AsbwounzmMelVgUUdIXs=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:27:34', 0);
INSERT INTO `product` VALUES (1007, 102, 2, '皇家成犬全价狗粮', 2, '中型成犬粮，含益生元呵护肠道，多规格可选', 89.00, 119.00, 450, 260, 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80', '[\"https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80\"]', 1, '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product` VALUES (1008, 102, 2, '渴望六种鱼无谷猫粮', 2, '85%动物原料，深海鱼配方，毛亮少泪痕', 158.00, 199.00, 200, 130, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/dcb9f857b63543628faddd7f38c79c09.png?e=1814254014&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:5cNpuv_BLcoxSwvqTf502Cqw1SE=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:26:58', 0);
INSERT INTO `product` VALUES (1009, 102, 2, '鸡肉冻干零食 100g', 2, '单一鸡胸肉冻干，高蛋白无添加，猫狗通用训练奖励', 39.90, 49.90, 500, 320, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/d6dac3ad26e84344a4120dffc8731db4.png?e=1814254039&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:bUjMcLTpjjfsUClTiYOdDZzBptk=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:27:22', 0);
INSERT INTO `product` VALUES (1010, 102, 2, '宠物营养主食罐头 170g', 2, '鸡肉+三文鱼配方，补水增肥，整箱更划算', 12.90, 16.90, 800, 540, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/f60630c5682041d69be62dff5bdb16e3.png?e=1814253978&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:lODsViCiK5PWdZMtbUli-v0AqEo=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:26:23', 0);
INSERT INTO `product` VALUES (1011, 102, 2, '磨牙洁齿狗咬胶（10支装）', 2, '天然马铃薯淀粉，清新口气、减少牙结石', 25.00, 35.00, 600, 210, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/27ae4a90bb5b4ee79f63845e6c553a62.png?e=1814253853&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:P2VC45kbviPAI2L0euagx3i4WHo=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:24:15', 0);
INSERT INTO `product` VALUES (1012, 103, 3, '逗猫棒·替换羽毛杆', 2, '伸缩杆+可替换羽毛头，逗猫神器久玩不腻', 9.90, 19.90, 1000, 880, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/c056884ca54a4f12aaa3d55305914de3.png?e=1814253941&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:s4N82XG97QnruseTCINsW0QZBaM=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:25:47', 0);
INSERT INTO `product` VALUES (1013, 103, 3, '宠物耐咬飞盘', 2, '软硅胶材质不伤齿，浮水设计户外互动', 19.90, 29.90, 400, 150, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/413ffa8537bd4e128e2efdc4059108f3.png?e=1814253931&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:gbV7WsMppxRAVOJg1clZSGEKB8c=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:25:34', 0);
INSERT INTO `product` VALUES (1014, 103, 3, '瓦楞纸猫抓板', 2, '加厚双面可用，耐抓护沙发，送猫薄荷', 29.90, 39.90, 350, 270, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/54ffd146d0664e35a8fbafbf8f570776.png?e=1814253922&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:8QhsibkfnabJ4_NYXgGz8CU5qb4=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:25:25', 0);
INSERT INTO `product` VALUES (1015, 103, 3, '发声毛绒玩具', 2, '内置发声器，柔软耐咬，缓解拆家焦虑', 15.90, 25.90, 750, 360, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/ee81fa3ecd07487494116fd717aa20b9.png?e=1814253912&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:O65kBZKkf_tWUl7d4dU59YxRFX0=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:25:16', 0);
INSERT INTO `product` VALUES (1016, 103, 4, '四季保暖宠物窝', 2, '可拆洗加厚棉垫，回弹支撑，多尺寸可选', 69.00, 99.00, 270, 220, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/683d341115f74db8b70bcba8add2a1c5.png?e=1814253896&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:B1_h4H3oaFAnxiWqYQLowyUE6xw=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:25:00', 0);
INSERT INTO `product` VALUES (1017, 101, 4, '可伸缩自动牵引绳', 2, '5米可收放，一键刹车，防爆冲护手柄', 39.00, 59.00, 450, 310, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/a4955a883a3e483f8031121d7a356f47.png?e=1814253885&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:EXeRFRqu_PNqHxtqPR6tg7ac2ng=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:24:48', 0);
INSERT INTO `product` VALUES (1018, 103, 4, '全自动智能猫砂盆', 2, 'APP远程监控，自动清理除臭，省心又卫生', 599.00, 899.00, 80, 45, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/13d13117cb694503865a37de6ca5c88f.png?e=1814253875&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:F5TJJRdj0Pf6UxeedXqpP7eX6-I=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:24:38', 0);
INSERT INTO `product` VALUES (1019, 101, 4, '不锈钢双碗食盆', 2, '防滑底座+倾斜设计，护颈椎易清洗', 45.00, 69.00, 300, 190, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/e27d4e3a86474fcfae57e98c497dfcf1.png?e=1814253869&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:mlcP7uDIaS6fQJizw2sQVDsIIkI=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:24:31', 0);
INSERT INTO `product` VALUES (1020, 103, 4, '宠物航空运输箱', 2, '加固卡扣，通风透气，符合托运标准', 129.00, 179.00, 150, 70, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/d6ced113c5874211ac6907707f3cc6ba.png?e=1814253861&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:Foj8ST_5pNVrty9K9at1PetK8XQ=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:24:23', 0);

-- ----------------------------
-- Table structure for product_category
-- ----------------------------
DROP TABLE IF EXISTS `product_category`;
CREATE TABLE `product_category`  (
  `id` bigint NOT NULL COMMENT '主键',
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父分类id 0=顶级',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分类名',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '图标',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_parent`(`parent_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品分类' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of product_category
-- ----------------------------
INSERT INTO `product_category` VALUES (1, 0, '宠物', 1, NULL, '2026-06-29 11:40:07', '2026-06-29 11:40:07', 0);
INSERT INTO `product_category` VALUES (2, 0, '宠物食品', 2, NULL, '2026-06-29 11:40:07', '2026-06-29 11:40:07', 0);
INSERT INTO `product_category` VALUES (3, 0, '宠物玩具', 3, NULL, '2026-06-29 11:40:07', '2026-06-29 11:40:07', 0);
INSERT INTO `product_category` VALUES (4, 0, '宠物用品', 4, NULL, '2026-06-29 11:40:07', '2026-06-29 11:40:07', 0);

-- ----------------------------
-- Table structure for product_sku
-- ----------------------------
DROP TABLE IF EXISTS `product_sku`;
CREATE TABLE `product_sku`  (
  `id` bigint NOT NULL COMMENT '主键',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `spec_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规格描述 如\"颜色:红;尺寸:L\"',
  `price` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '该规格价格',
  `stock` int NOT NULL DEFAULT 0 COMMENT '该规格库存',
  `image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '规格图',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_product`(`product_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品规格SKU' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of product_sku
-- ----------------------------
INSERT INTO `product_sku` VALUES (5001, 1007, '规格:2kg', 89.00, 200, 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5002, 1007, '规格:5kg', 199.00, 150, 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5003, 1007, '规格:10kg', 369.00, 100, 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5004, 1008, '规格:1.8kg', 158.00, 120, 'https://images.unsplash.com/photo-1623341214825-9f4f963727da?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5005, 1008, '规格:5.4kg', 398.00, 80, 'https://images.unsplash.com/photo-1623341214825-9f4f963727da?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5006, 1015, '款式:小黄鸭', 15.90, 300, 'https://images.unsplash.com/photo-1572085313466-6710de8d7ba3?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5007, 1015, '款式:小熊', 15.90, 250, 'https://images.unsplash.com/photo-1572085313466-6710de8d7ba3?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5008, 1015, '款式:小象', 17.90, 200, 'https://images.unsplash.com/photo-1572085313466-6710de8d7ba3?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5009, 1016, '尺寸:S(5kg内)', 69.00, 120, 'https://images.unsplash.com/photo-1541781774459-bb2af2f05b55?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5010, 1016, '尺寸:M(10kg内)', 99.00, 90, 'https://images.unsplash.com/photo-1541781774459-bb2af2f05b55?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5011, 1016, '尺寸:L(20kg内)', 139.00, 60, 'https://images.unsplash.com/photo-1541781774459-bb2af2f05b55?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5012, 1017, '颜色:红色', 39.00, 150, 'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5013, 1017, '颜色:蓝色', 39.00, 140, 'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5014, 1017, '颜色:黑色', 39.00, 160, 'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5015, 1020, '尺寸:S', 129.00, 70, 'https://images.unsplash.com/photo-1518791841217-8f162f1e1131?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5016, 1020, '尺寸:M', 169.00, 50, 'https://images.unsplash.com/photo-1518791841217-8f162f1e1131?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product_sku` VALUES (5017, 1020, '尺寸:L', 229.00, 30, 'https://images.unsplash.com/photo-1518791841217-8f162f1e1131?auto=format&fit=crop&w=400&q=80', '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);

-- ----------------------------
-- Table structure for recommend_result
-- ----------------------------
DROP TABLE IF EXISTS `recommend_result`;
CREATE TABLE `recommend_result`  (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `product_id` bigint NOT NULL COMMENT '推荐商品id',
  `score` decimal(10, 4) NOT NULL DEFAULT 0.0000 COMMENT '推荐得分',
  `source` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'UCF' COMMENT '算法来源 UCF=基于用户',
  `create_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_product`(`user_id` ASC, `product_id` ASC) USING BTREE,
  INDEX `idx_user_score`(`user_id` ASC, `score` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '推荐结果' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of recommend_result
-- ----------------------------

-- ----------------------------
-- Table structure for refund
-- ----------------------------
DROP TABLE IF EXISTS `refund`;
CREATE TABLE `refund`  (
  `id` bigint NOT NULL COMMENT '主键',
  `refund_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '退单号',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `amount` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '退款金额',
  `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '退单理由',
  `type` tinyint NOT NULL DEFAULT 1 COMMENT '1用户申请 2管理员直接退',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0申请中 1审核通过(已退) 2审核拒绝',
  `audit_user_id` bigint NULL DEFAULT NULL COMMENT '审核管理员id',
  `audit_time` datetime NULL DEFAULT NULL COMMENT '审核时间',
  `audit_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '审核备注',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_refund_no`(`refund_no` ASC) USING BTREE,
  INDEX `idx_order`(`order_id` ASC) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '退单' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of refund
-- ----------------------------

-- ----------------------------
-- Table structure for review
-- ----------------------------
DROP TABLE IF EXISTS `review`;
CREATE TABLE `review`  (
  `id` bigint NOT NULL COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `order_item_id` bigint NOT NULL COMMENT '订单明细id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `shop_id` bigint NULL DEFAULT NULL COMMENT '商店id',
  `rating` tinyint NOT NULL DEFAULT 5 COMMENT '评分 1-5',
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '评价内容',
  `images` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '评价图(JSON)',
  `reply` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '商家回复',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_item`(`order_item_id` ASC) USING BTREE,
  INDEX `idx_order`(`order_id` ASC) USING BTREE,
  INDEX `idx_product`(`product_id` ASC) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '评价' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of review
-- ----------------------------

-- ----------------------------
-- Table structure for shop
-- ----------------------------
DROP TABLE IF EXISTS `shop`;
CREATE TABLE `shop`  (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商店名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '商店简介',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系电话',
  `province` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '省',
  `city` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '市',
  `district` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '区/县',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '详细地址',
  `longitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '经度（地图找附近用）',
  `latitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '纬度（地图找附近用）',
  `logo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '商店logo',
  `owner_id` bigint NULL DEFAULT NULL COMMENT '店主用户id',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1营业 0停业',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_owner`(`owner_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商店' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of shop
-- ----------------------------
INSERT INTO `shop` VALUES (101, '萌宠之家', '专注活体宠物与日常用品，正规渠道、健康保障', '0571-88880001', '浙江省', '杭州市', '西湖区', '文三路 100 号宠物广场 1 层', 120.130000, 30.279000, 'https://images.unsplash.com/photo-1517849845537-4d257902454a?auto=format&fit=crop&w=400&q=80', 3, 1, '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `shop` VALUES (102, '喵汪优粮馆', '进口主粮 / 冻干 / 零食一站式补给', '0571-88880002', '浙江省', '杭州市', '拱墅区', '莫干山路 50 号 B 座 2 层', 120.140000, 30.320000, 'https://images.unsplash.com/photo-1573865526739-10659fec78a5?auto=format&fit=crop&w=400&q=80', 4, 1, '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `shop` VALUES (103, '趣玩宠物', '玩具 / 窝垫 / 出行装备，让毛孩子更开心', '0571-88880003', '浙江省', '杭州市', '滨江区', '江南大道 228 号宠乐汇 3 层', 120.210000, 30.205000, 'https://images.unsplash.com/photo-1537151608804-ea2aa1427189?auto=format&fit=crop&w=400&q=80', 4, 1, '2026-06-24 18:04:57', '2026-06-29 15:02:02', 0);

-- ----------------------------
-- Table structure for sys_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_log`;
CREATE TABLE `sys_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '操作人ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作人用户名',
  `operation` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作模块/描述',
  `method` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '请求方法',
  `params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '请求参数',
  `time` bigint NULL DEFAULT NULL COMMENT '执行时间(毫秒)',
  `ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'IP地址',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统操作日志' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_log
-- ----------------------------
INSERT INTO `sys_log` VALUES (1, NULL, NULL, '用户登录', 'com.petshop.controller.AuthController.login()', '{\"username\":\"admin\",\"password\":\"123456\"}', 289, '127.0.0.1', '2026-06-29 14:52:42');
INSERT INTO `sys_log` VALUES (2, NULL, NULL, '用户注册', 'com.petshop.controller.AuthController.register()', '{\"username\":\"user\",\"password\":\"123456\",\"nickname\":\"\",\"phone\":\"\",\"email\":\"\"}', 97, '127.0.0.1', '2026-06-29 14:53:02');
INSERT INTO `sys_log` VALUES (3, NULL, NULL, '用户登录', 'com.petshop.controller.AuthController.login()', '{\"username\":\"user\",\"password\":\"123456\"}', 81, '127.0.0.1', '2026-06-29 14:53:07');
INSERT INTO `sys_log` VALUES (4, NULL, NULL, '用户登录', 'com.petshop.controller.AuthController.login()', '{\"username\":\"admin\",\"password\":\"123456\"}', 239, '127.0.0.1', '2026-06-29 14:53:33');
INSERT INTO `sys_log` VALUES (5, NULL, '123456', '用户注册', 'com.petshop.controller.AuthController.register()', '{\"username\":\"123456\",\"password\":\"123456\",\"nickname\":\"\",\"phone\":\"\",\"email\":\"\"}', 92, '127.0.0.1', '2026-06-29 14:55:53');
INSERT INTO `sys_log` VALUES (6, NULL, '123456', '用户登录', 'com.petshop.controller.AuthController.login()', '{\"username\":\"123456\",\"password\":\"123456\"}', 296, '127.0.0.1', '2026-06-29 14:55:59');
INSERT INTO `sys_log` VALUES (7, NULL, 'admin', '用户登录', 'com.petshop.controller.AuthController.login()', '{\"username\":\"admin\",\"password\":\"123456\"}', 246, '127.0.0.1', '2026-06-29 14:56:21');
INSERT INTO `sys_log` VALUES (8, NULL, 'admin', '用户登录', 'com.petshop.controller.AuthController.login()', '{\"username\":\"admin\",\"password\":\"123456\"}', 270, '127.0.0.1', '2026-06-29 15:32:11');

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint NOT NULL COMMENT '主键',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户名(唯一,逻辑删除后不复用)',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密码(BCrypt加密)',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '头像',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮箱',
  `gender` tinyint NOT NULL DEFAULT 0 COMMENT '0未知 1男 2女',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN/MERCHANT',
  `member_level_id` bigint NOT NULL DEFAULT 0 COMMENT '会员等级id 0=游客/非会员',
  `balance` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '账户余额(余额支付用)',
  `points` int NOT NULL DEFAULT 0 COMMENT '积分',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  `last_login_time` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` VALUES (1, 'admin', '$2a$12$AYxVk1sMLdm346ln91x8R.pKKCTNGtQWts8mfnefngMD/q0BBnuUC', '管理员', NULL, NULL, NULL, 0, 'ADMIN', 0, 0.00, 0, 1, '2026-06-29 15:32:11', '2026-06-29 11:40:07', '2026-06-29 11:40:07', 0);
INSERT INTO `user` VALUES (2, 'test', '$2a$12$AYxVk1sMLdm346ln91x8R.pKKCTNGtQWts8mfnefngMD/q0BBnuUC', '测试用户', NULL, NULL, NULL, 0, 'USER', 1, 0.00, 0, 1, NULL, '2026-06-29 11:40:07', '2026-06-29 11:40:07', 0);
INSERT INTO `user` VALUES (3, 'shop1', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '萌宠之家店主', NULL, NULL, NULL, 0, 'MERCHANT', 0, 0.00, 0, 1, NULL, '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `user` VALUES (4, 'shop2', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '趣玩宠物店主', NULL, NULL, NULL, 0, 'MERCHANT', 0, 0.00, 0, 1, NULL, '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `user` VALUES (2071487084287275010, 'user', '$2a$10$j1uUuECssOl29EbuDN1eROECKgElSiBZHvE0tXizKZVZHfgljs/E.', '', NULL, '', '', 0, 'USER', 0, 0.00, 0, 1, '2026-06-29 14:53:07', '2026-06-29 14:53:02', '2026-06-29 14:53:02', 0);
INSERT INTO `user` VALUES (2071487801290919938, '123456', '$2a$10$IMRl4cs0LsC24dCQPWeeieIKoygez7WOGLlZzjBpgpfLrgipCPc/i', '', NULL, '', '', 0, 'USER', 0, 0.00, 0, 1, '2026-06-29 14:55:59', '2026-06-29 14:55:53', '2026-06-29 14:55:53', 0);

-- ----------------------------
-- Table structure for user_behavior
-- ----------------------------
DROP TABLE IF EXISTS `user_behavior`;
CREATE TABLE `user_behavior`  (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `behavior_type` tinyint NOT NULL COMMENT '1浏览 2收藏 3加购 4购买',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_product`(`product_id` ASC) USING BTREE,
  INDEX `idx_behavior`(`behavior_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户行为记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_behavior
-- ----------------------------

-- ----------------------------
-- Table structure for user_coupon
-- ----------------------------
DROP TABLE IF EXISTS `user_coupon`;
CREATE TABLE `user_coupon`  (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `coupon_id` bigint NOT NULL COMMENT '券id',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0未使用 1已使用 2已过期',
  `used_time` datetime NULL DEFAULT NULL COMMENT '使用时间',
  `order_id` bigint NOT NULL DEFAULT 0 COMMENT '使用的订单id',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户优惠券' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_coupon
-- ----------------------------

-- ----------------------------
-- Table structure for user_item_score
-- ----------------------------
DROP TABLE IF EXISTS `user_item_score`;
CREATE TABLE `user_item_score`  (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `product_id` bigint NOT NULL COMMENT '商品id',
  `score` decimal(8, 4) NOT NULL DEFAULT 0.0000 COMMENT '隐式评分=行为加权',
  `update_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_product`(`user_id` ASC, `product_id` ASC) USING BTREE,
  INDEX `idx_product`(`product_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户商品评分矩阵' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_item_score
-- ----------------------------

-- ----------------------------
-- Table structure for user_message
-- ----------------------------
DROP TABLE IF EXISTS `user_message`;
CREATE TABLE `user_message`  (
  `id` bigint NOT NULL COMMENT '主键',
  `message_id` bigint NOT NULL COMMENT '消息id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `is_read` tinyint NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `read_time` datetime NULL DEFAULT NULL COMMENT '阅读时间',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_message_user`(`message_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户消息已读状态' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_message
-- ----------------------------

-- ----------------------------
-- Table structure for user_oauth
-- ----------------------------
DROP TABLE IF EXISTS `user_oauth`;
CREATE TABLE `user_oauth`  (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `provider` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'wechat/qq/...',
  `open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '第三方openid',
  `union_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'unionid',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_openid`(`provider` ASC, `open_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '第三方登录绑定' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_oauth
-- ----------------------------

-- ----------------------------
-- Table structure for user_similarity
-- ----------------------------
DROP TABLE IF EXISTS `user_similarity`;
CREATE TABLE `user_similarity`  (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '目标用户',
  `sim_user_id` bigint NOT NULL COMMENT '相似用户',
  `similarity` decimal(8, 6) NOT NULL DEFAULT 0.000000 COMMENT '相似度 0~1',
  `update_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_sim`(`user_id` ASC, `sim_user_id` ASC) USING BTREE,
  INDEX `idx_user_sim`(`user_id` ASC, `similarity` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户相似度' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_similarity
-- ----------------------------

-- ----------------------------
-- Table structure for video
-- ----------------------------
DROP TABLE IF EXISTS `video`;
CREATE TABLE `video`  (
  `id` bigint NOT NULL COMMENT '主键',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标题',
  `cover` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '封面',
  `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '视频地址',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '简介',
  `product_id` bigint NOT NULL DEFAULT 0 COMMENT '关联商品id 0=无',
  `shop_id` bigint NOT NULL DEFAULT 0 COMMENT '关联商店id',
  `views` int NOT NULL DEFAULT 0 COMMENT '播放量',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_product`(`product_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '宠物视频' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of video
-- ----------------------------
INSERT INTO `video` VALUES (2071496372720361473, '4个月大的母英国短毛蓝猫', 'http://th5y5cvlf.hd-bkt.clouddn.com/images/536f7f9cd50546f4a852dc280d075995.png?e=1814254127&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:5ahu_oNxPFFo5wnXJ2UXAaos3Rs=', 'http://th5y5cvlf.hd-bkt.clouddn.com/videos/7b5193b0f2e64ed0986b0f63502e3983.mp4?e=1814254116&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:EOir6dmbEc_YPS_c5tYKdKiEGUY=', '4个月大的母英国短毛蓝猫', 1002, 101, 3, 1, '2026-06-29 15:29:56', '2026-06-29 15:32:44', 0);

SET FOREIGN_KEY_CHECKS = 1;
