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

 Date: 02/07/2026 16:39:38
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
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of product
-- ----------------------------
INSERT INTO `product` VALUES (1001, 101, 1, '金毛寻回犬幼犬（公）', 1, '3个月大，已打疫苗驱虫，性格温顺亲人，附血统证明', 3200.00, 3200.00, 1, 6, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/8e42e7d72fd74e408212aa51165309e0.png?e=1814516740&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:NmEen3aIqp_HKg8nowdP78FhRbA=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 16:25:42', 0);
INSERT INTO `product` VALUES (1002, 101, 1, '英国短毛猫·蓝猫（母）', 1, '4个月，包子脸，疫苗齐全，可上门看猫', 2200.00, 2200.00, 1, 9, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/af5e8f90653947a79d6cb5b71b6615b3.png?e=1814516677&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:KS1dN0AzTA-uOITtMDpo71UwXyI=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 16:24:39', 0);
INSERT INTO `product` VALUES (1003, 101, 1, '布偶猫·海双（母）', 1, 'CFA血统，眼睛蓝，毛量足，附绝育保障', 6500.00, 6500.00, 1, 3, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/9aade5f4d7a246ac845d9440daf50bbb.jpg?e=1814515132&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:Q2FcXdxPmMArVhulnWFmH_xGT40=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 15:58:55', 0);
INSERT INTO `product` VALUES (1004, 101, 1, '柯基幼犬·三色（公）', 1, '短腿大屁股，活泼好动，已做基础免疫', 3900.00, 3900.00, 1, 5, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/2e1c1dffc350454ab8bc5130d36ae2e8.jpg?e=1814515142&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:z3ofVnXW9wDovfoiyFOKxbiyLpc=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 15:59:04', 0);
INSERT INTO `product` VALUES (1005, 101, 1, '荷兰侏儒兔', 1, '成年体重 1kg 左右，温顺易养，附兔粮试用装', 280.00, 360.00, 1, 14, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/aff20ef5bc52412ebee7a7df2fb7dea8.png?e=1814406171&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:gsfQDRi-XTEdh-FJP_8-Tw0Xkj4=', '[]', 1, '2026-06-24 18:04:57', '2026-07-01 09:42:52', 0);
INSERT INTO `product` VALUES (1006, 101, 1, '玄凤鹦鹉·珍珠', 1, '手养亲人，可上手，含证书与饲养手册', 420.00, 420.00, 1, 8, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/a9595235dfa844028929c8dec8a676b3.jpg?e=1814515149&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:lydwb8U_tlfiXdehz5WddeNo1fw=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 15:59:10', 0);
INSERT INTO `product` VALUES (1007, 102, 2, '皇家成犬全价狗粮', 2, '中型成犬粮，含益生元呵护肠道，多规格可选', 89.00, 119.00, 450, 260, 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80', '[\"https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80\"]', 1, '2026-06-24 18:04:57', '2026-06-24 18:04:57', 0);
INSERT INTO `product` VALUES (1008, 102, 2, '渴望六种鱼无谷猫粮', 2, '85%动物原料，深海鱼配方，毛亮少泪痕', 199.00, 199.00, 200, 130, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/33b92355d76842648885b2bdeb8e703c.jpg?e=1814516766&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:woyqSASJR_bznwIGv4DTOw2302w=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 16:26:15', 0);
INSERT INTO `product` VALUES (1009, 102, 2, '鸡肉冻干零食 100g', 2, '单一鸡胸肉冻干，高蛋白无添加，猫狗通用训练奖励', 49.90, 49.90, 500, 320, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/d526a1b2781c45e799060b6fb9db6bf8.jpg?e=1814516756&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:Lo10yifSUEad2H45N4MMB2cWEVI=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 16:25:58', 0);
INSERT INTO `product` VALUES (1010, 102, 2, '宠物营养主食罐头 170g', 2, '鸡肉+三文鱼配方，补水增肥，整箱更划算', 16.90, 16.90, 800, 540, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/298fc0301bce4995ae38a8104bcbf912.jpg?e=1814516668&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:WBgUSeuT-Mc9rOXDo6qP9GjwzU0=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 16:24:29', 0);
INSERT INTO `product` VALUES (1011, 102, 2, '磨牙洁齿狗咬棒（10支装）', 2, '天然马铃薯淀粉，清新口气、减少牙结石', 35.00, 35.00, 600, 210, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/dbed50c9be714ff682a4baa30a5acf55.jpg?e=1814516731&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:pVTREg75HE3xD-eXpRoKrutiKsU=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 16:25:32', 0);
INSERT INTO `product` VALUES (1012, 103, 3, '逗猫棒·替换羽毛杆', 2, '伸缩杆+可替换羽毛头，逗猫神器久玩不腻', 19.90, 19.90, 999, 880, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/9324403ed4364bf9b14dc3fe4ebde491.jpg?e=1814516710&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:A0SfUK2kL-qfFkRayKUgna52PSw=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 16:25:11', 0);
INSERT INTO `product` VALUES (1013, 103, 3, '宠物耐咬飞盘', 2, '软硅胶材质不伤齿，浮水设计户外互动', 29.90, 29.90, 399, 150, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/c1b306b8a8534dd3a27180013c3e2f14.jpg?e=1814516721&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:wpN6XQ7lGQWij3iIDU4tGe4IHHw=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 16:25:22', 0);
INSERT INTO `product` VALUES (1014, 103, 3, '小玩偶', 2, '加厚双面可用，耐抓护沙发，送猫薄荷', 39.90, 39.90, 350, 270, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/45001caaccce40efa2ee0b6e52f47b3f.jpg?e=1814516847&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:zn7M3A94R09Bs3nAYJzBH5AiAZ0=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 16:27:47', 0);
INSERT INTO `product` VALUES (1015, 103, 3, '瓦楞纸猫抓板', 2, '内置发声器，柔软耐咬，缓解拆家焦虑', 15.90, 25.90, 750, 360, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/309908ac32674e18a7622720381cdaa1.png?e=1814406433&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:B9ex_WiMHbW0vrh5_EKJ8_syAsU=', '[]', 1, '2026-06-24 18:04:57', '2026-07-01 09:47:14', 0);
INSERT INTO `product` VALUES (1016, 103, 4, '四季保暖宠物窝', 2, '可拆洗加厚棉垫，回弹支撑，多尺寸可选', 99.00, 99.00, 270, 220, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/9dec25cddbfe42bf86b60bc3396ac970.png?e=1814406438&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:g4ljdMDlXbUNNlLuDxE6tBtx2N8=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 16:27:18', 0);
INSERT INTO `product` VALUES (1017, 101, 4, '可伸缩自动牵引绳', 2, '5米可收放，一键刹车，防爆冲护手柄', 39.00, 59.00, 450, 310, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/a4955a883a3e483f8031121d7a356f47.png?e=1814253885&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:EXeRFRqu_PNqHxtqPR6tg7ac2ng=', '[]', 1, '2026-06-24 18:04:57', '2026-06-29 15:24:48', 0);
INSERT INTO `product` VALUES (1018, 103, 4, '全自动智能猫砂盆', 2, 'APP远程监控，自动清理除臭，省心又卫生', 899.00, 899.00, 80, 45, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/fd6fe2043abb4e85b8cd58224c50931c.jpg?e=1814516598&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:ZW33d7oyWosesh0wCxZb0I8Yg3Q=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 16:23:27', 0);
INSERT INTO `product` VALUES (1019, 101, 4, '不锈钢双碗食盆', 2, '防滑底座+倾斜设计，护颈椎易清洗', 69.00, 69.00, 299, 190, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/bfad2ceef9a14156a54f583d394b8a52.jpg?e=1814516649&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:XE5yV5Bfr0RuBdvqQu0iKg13Cps=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 16:24:10', 0);
INSERT INTO `product` VALUES (1020, 103, 4, '宠物航空运输箱', 2, '加固卡扣，通风透气，符合托运标准', 179.00, 179.00, 150, 70, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/f7d526a133014e7490d84c73dedcb7bb.png?e=1814406457&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:XubBSHIwAPbYyDCJJVPJ9_UZfGo=', '[]', 1, '2026-06-24 18:04:57', '2026-07-02 16:23:59', 0);
INSERT INTO `product` VALUES (1021, 104, 3, '发声毛绒小熊玩具', 2, '内置发声气囊，耐咬材质，狗狗最爱', 25.00, 35.00, 500, 80, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/ed33f6ef797946d5ace6a89a50ff354e.png?e=1814405758&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:gHAy_0yEa1TnQy9_Yhg1wS0GzKY=', '[]', 1, '2026-06-30 16:11:17', '2026-07-01 09:36:02', 0);
INSERT INTO `product` VALUES (1022, 104, 4, '全封闭式防臭猫砂盆', 2, '超大空间，带落砂踏板，有效隔绝异味', 129.00, 129.00, 150, 45, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/72380ab80e9343fcbdffcd334eb7a147.jpg?e=1814516503&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:uYV4O2B1riFXeKib8z2mOg12xKE=', '[]', 1, '2026-06-30 16:11:17', '2026-07-02 16:21:44', 0);
INSERT INTO `product` VALUES (1023, 105, 2, '深海鱼油美毛狗粮 5kg', 2, '添加三文鱼油，亮泽毛发，适合中大型犬', 239.00, 289.00, 200, 210, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/41463ae040f3417aa24cb11641e9fc0a.png?e=1814405844&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:wzTvzDzKIkBIL41WBeKin3xUm-s=', '[]', 1, '2026-06-30 16:11:17', '2026-07-01 09:37:26', 0);
INSERT INTO `product` VALUES (1024, 105, 3, '自动漏食不倒翁', 2, '边玩边吃，缓解焦虑，提升智力', 59.00, 59.00, 400, 160, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/45f54cc0025046d7a6cc3f7fbed549a7.jpg?e=1814516522&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:66433rp-5-9jwYj4sdffd9rbfww=', '[]', 1, '2026-06-30 16:11:17', '2026-07-02 16:22:03', 0);
INSERT INTO `product` VALUES (1025, 105, 4, '宠物车载安全座椅', 2, '防水防滑，保障出行安全，中小型犬适用', 198.00, 198.00, 100, 30, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/f1f0dd53e0b045c895c192b0f6d4bf93.jpg?e=1814516531&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:gCWjkAOB2q4Jgrr4jFA_H3qkz7E=', '[]', 1, '2026-06-30 16:11:17', '2026-07-02 16:22:13', 0);
INSERT INTO `product` VALUES (1026, 106, 2, '主食冻干双拼犬粮', 2, '肉松冻干+高品质干粮，挑食克星', 228.00, 228.00, 250, 330, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/8492df705c0340c9ba3bb14eb93a143f.jpg?e=1814516545&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:mWVxWmj05o_UA7DEc3ZTI6ievh4=', '[]', 1, '2026-06-30 16:11:17', '2026-07-02 16:22:45', 0);
INSERT INTO `product` VALUES (1027, 106, 3, '猫薄荷毛绒仿真鱼', 2, '逼真造型，内含浓郁猫薄荷，让猫咪欲罢不能', 15.00, 25.00, 799, 540, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/addbb75d31bd4eb395b4bae53c5106aa.png?e=1814406011&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:eOpVRusXXXWnDrPjo_NxRIR4Yw4=', '[]', 1, '2026-06-30 16:11:17', '2026-07-01 09:40:11', 0);
INSERT INTO `product` VALUES (1028, 106, 4, '智能恒温宠物饮水机', 2, '多重过滤，APP温控，让爱宠爱上喝水', 299.00, 299.00, 120, 90, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/a600e3bd61b74c30ab08112efc5b851a.jpg?e=1814516585&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:f6nwOpmDmQyk9PQyhI3zV2yNlqI=', '[]', 1, '2026-06-30 16:11:17', '2026-07-02 16:23:09', 0);
INSERT INTO `product` VALUES (1029, 104, 2, '皇家成犬全价狗粮 (胖虎宠物超市)', 2, '中型成犬粮，含益生元呵护肠道，多规格可选', 119.00, 119.00, 450, 260, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/4a6e82d9f027415ea76ba7faba2a4428.jpg?e=1814516410&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:goDKht6h7O-Uk5qvnug3Nqg7lXc=', '[]', 1, '2026-06-30 16:16:34', '2026-07-02 16:20:28', 0);
INSERT INTO `product` VALUES (1030, 104, 3, '宠物耐咬飞盘 (胖虎宠物超市)', 2, '软硅胶材质不伤齿，浮水设计户外互动', 29.90, 29.90, 400, 150, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/df3dcfe4c08d4195a3b7f1d333e481d6.jpg?e=1814516433&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:6mzWsZY8ethfbvHgc76GORXfEXU=', '[]', 1, '2026-06-30 16:16:34', '2026-07-02 16:20:35', 0);
INSERT INTO `product` VALUES (1031, 105, 2, '渴望六种鱼无谷猫粮 (金牌宠粮专卖)', 2, '85%动物原料，深海鱼配方，毛亮少泪痕', 199.00, 199.00, 200, 130, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/70d89fb77ba043d1b46062a1f7da740f.jpg?e=1814516441&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:6lgqie77t-OeYwlnqhRUB1iXz2c=', '[]', 1, '2026-06-30 16:16:34', '2026-07-02 16:20:51', 0);
INSERT INTO `product` VALUES (1032, 105, 2, '鸡肉冻干零食 100g (金牌宠粮专卖)', 2, '单一鸡胸肉冻干，高蛋白无添加，猫狗通用训练奖励', 49.90, 49.90, 500, 320, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/b7421d1c7df04291912113f378ba5b53.jpg?e=1814516493&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:ueYKqLuYlFaMrLdjRx942ummG4s=', '[]', 1, '2026-06-30 16:16:34', '2026-07-02 16:21:34', 0);
INSERT INTO `product` VALUES (1033, 106, 3, '瓦楞纸猫抓板 (潮宠装备库)', 2, '加厚双面可用，耐抓护沙发，送猫薄荷', 39.90, 39.90, 350, 270, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/bb1fc9e5a13a4fcda523e674e3ad7dcd.png?e=1814405881&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:xo6gRAWR0EiiOL7n8qDQZYrcWgs=', '[]', 1, '2026-06-30 16:16:34', '2026-07-02 16:20:58', 0);
INSERT INTO `product` VALUES (1034, 106, 3, '磨牙洁齿狗咬胶 (潮宠装备库)', 2, '天然马铃薯淀粉，清新口气、减少牙结石', 35.00, 35.00, 600, 210, 'http://th5y5cvlf.hd-bkt.clouddn.com/images/2f7bfbe1e5ca4b8ebc1e56f92afebd93.jpg?e=1814516348&token=au9DbHeMHDmgU1zr3QJY7sh78Q0O7HEz6-j6y-4Y:xB6A0oTEeZHPLnmhUmY0zmm2C0M=', '[]', 1, '2026-06-30 16:16:34', '2026-07-02 16:19:09', 0);

SET FOREIGN_KEY_CHECKS = 1;
