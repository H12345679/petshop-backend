-- ============================================================
-- 商品模块数据导出（由数据库实际数据 mysqldump 生成）
-- 含：商家用户(role=MERCHANT) + 商店 + 商品 + 商品规格SKU
-- 依赖：init.sql 已建表、product_category(1~4) 已存在。
-- 导入：docker exec -i petshop-mysql mysql -uroot -proot petshop < sql/product-data.sql
-- ============================================================
SET NAMES utf8mb4;
USE petshop;


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `avatar`, `phone`, `email`, `gender`, `role`, `member_level_id`, `balance`, `points`, `status`, `last_login_time`, `create_time`, `update_time`, `deleted`) VALUES (3,'shop1','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','萌宠之家店主',NULL,NULL,NULL,0,'MERCHANT',0,0.00,0,1,NULL,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `avatar`, `phone`, `email`, `gender`, `role`, `member_level_id`, `balance`, `points`, `status`, `last_login_time`, `create_time`, `update_time`, `deleted`) VALUES (4,'shop2','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','趣玩宠物店主',NULL,NULL,NULL,0,'MERCHANT',0,0.00,0,1,NULL,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `shop` WRITE;
/*!40000 ALTER TABLE `shop` DISABLE KEYS */;
INSERT INTO `shop` (`id`, `name`, `description`, `phone`, `province`, `city`, `district`, `address`, `longitude`, `latitude`, `logo`, `owner_id`, `status`, `create_time`, `update_time`, `deleted`) VALUES (101,'萌宠之家','专注活体宠物与日常用品，正规渠道、健康保障','0571-88880001','浙江省','杭州市','西湖区','文三路 100 号宠物广场 1 层',120.130000,30.279000,'https://images.unsplash.com/photo-1517849845537-4d257902454a?auto=format&fit=crop&w=400&q=80',3,1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `shop` (`id`, `name`, `description`, `phone`, `province`, `city`, `district`, `address`, `longitude`, `latitude`, `logo`, `owner_id`, `status`, `create_time`, `update_time`, `deleted`) VALUES (102,'喵汪优粮馆','进口主粮 / 冻干 / 零食一站式补给','0571-88880002','浙江省','杭州市','拱墅区','莫干山路 50 号 B 座 2 层',120.140000,30.320000,'https://images.unsplash.com/photo-1573865526739-10659fec78a5?auto=format&fit=crop&w=400&q=80',4,1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `shop` (`id`, `name`, `description`, `phone`, `province`, `city`, `district`, `address`, `longitude`, `latitude`, `logo`, `owner_id`, `status`, `create_time`, `update_time`, `deleted`) VALUES (103,'趣玩宠物','玩具 / 窝垫 / 出行装备，让毛孩子更开心','0571-88880003','浙江省','杭州市','滨江区','江南大道 228 号宠乐汇 3 层',120.210000,30.205000,'https://images.unsplash.com/photo-1537151608804-ea2aa1427189?auto=format&fit=crop&w=400&q=80',4,1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
/*!40000 ALTER TABLE `shop` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `product` WRITE;
/*!40000 ALTER TABLE `product` DISABLE KEYS */;
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1001,101,1,'金毛寻回犬幼犬（公）',1,'3个月大，已打疫苗驱虫，性格温顺亲人，附血统证明',2800.00,3200.00,1,6,'https://images.unsplash.com/photo-1552053831-71594a27632d?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1552053831-71594a27632d?auto=format&fit=crop&w=400&q=80\",\"https://images.unsplash.com/photo-1552053831-71594a27632d?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1002,101,1,'英国短毛猫·蓝猫（母）',1,'4个月，包子脸，疫苗齐全，可上门看猫',1800.00,2200.00,1,9,'https://images.unsplash.com/photo-1513245543132-31f507417b26?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1513245543132-31f507417b26?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1003,101,1,'布偶猫·海双（母）',1,'CFA血统，眼睛蓝，毛量足，附绝育保障',5800.00,6500.00,1,3,'https://images.unsplash.com/photo-1574158622682-e40e69881006?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1574158622682-e40e69881006?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1004,101,1,'柯基幼犬·三色（公）',1,'短腿大屁股，活泼好动，已做基础免疫',3500.00,3900.00,1,5,'https://images.unsplash.com/photo-1548199973-03cce0bbc87b?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1548199973-03cce0bbc87b?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1005,101,1,'荷兰侏儒兔',1,'成年体重 1kg 左右，温顺易养，附兔粮试用装',280.00,360.00,1,14,'https://images.unsplash.com/photo-1585110396000-c9ffd4e4b308?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1585110396000-c9ffd4e4b308?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1006,101,1,'玄凤鹦鹉·珍珠',1,'手养亲人，可上手，含证书与饲养手册',360.00,420.00,1,8,'https://images.unsplash.com/photo-1552728089-57168bb3ea30?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1552728089-57168bb3ea30?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1007,102,2,'皇家成犬全价狗粮',2,'中型成犬粮，含益生元呵护肠道，多规格可选',89.00,119.00,450,260,'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1008,102,2,'渴望六种鱼无谷猫粮',2,'85%动物原料，深海鱼配方，毛亮少泪痕',158.00,199.00,200,130,'https://images.unsplash.com/photo-1623341214825-9f4f963727da?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1623341214825-9f4f963727da?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1009,102,2,'鸡肉冻干零食 100g',2,'单一鸡胸肉冻干，高蛋白无添加，猫狗通用训练奖励',39.90,49.90,500,320,'https://images.unsplash.com/photo-1601001815894-ea460c0cb3cc?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1601001815894-ea460c0cb3cc?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1010,102,2,'宠物营养主食罐头 170g',2,'鸡肉+三文鱼配方，补水增肥，整箱更划算',12.90,16.90,800,540,'https://images.unsplash.com/photo-1529257414772-1960b7bea4eb?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1529257414772-1960b7bea4eb?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1011,102,2,'磨牙洁齿狗咬胶（10支装）',2,'天然马铃薯淀粉，清新口气、减少牙结石',25.00,35.00,600,210,'https://images.unsplash.com/photo-1629851608405-f9e4215904fc?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1629851608405-f9e4215904fc?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1012,103,3,'逗猫棒·替换羽毛杆',2,'伸缩杆+可替换羽毛头，逗猫神器久玩不腻',9.90,19.90,1000,880,'https://images.unsplash.com/photo-1513360371669-4adf3dd7dff8?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1513360371669-4adf3dd7dff8?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1013,103,3,'宠物耐咬飞盘',2,'软硅胶材质不伤齿，浮水设计户外互动',19.90,29.90,400,150,'https://images.unsplash.com/photo-1602491453631-e2a5ad90a131?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1602491453631-e2a5ad90a131?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1014,103,3,'瓦楞纸猫抓板',2,'加厚双面可用，耐抓护沙发，送猫薄荷',29.90,39.90,350,270,'https://images.unsplash.com/photo-1520315342629-6ea920342047?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1520315342629-6ea920342047?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1015,103,3,'发声毛绒玩具',2,'内置发声器，柔软耐咬，缓解拆家焦虑',15.90,25.90,750,360,'https://images.unsplash.com/photo-1572085313466-6710de8d7ba3?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1572085313466-6710de8d7ba3?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1016,103,4,'四季保暖宠物窝',2,'可拆洗加厚棉垫，回弹支撑，多尺寸可选',69.00,99.00,270,220,'https://images.unsplash.com/photo-1541781774459-bb2af2f05b55?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1541781774459-bb2af2f05b55?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1017,101,4,'可伸缩自动牵引绳',2,'5米可收放，一键刹车，防爆冲护手柄',39.00,59.00,450,310,'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1576201836106-db1758fd1c97?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1018,103,4,'全自动智能猫砂盆',2,'APP远程监控，自动清理除臭，省心又卫生',599.00,899.00,80,45,'https://images.unsplash.com/photo-1587300003388-59208cc962cb?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1587300003388-59208cc962cb?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1019,101,4,'不锈钢双碗食盆',2,'防滑底座+倾斜设计，护颈椎易清洗',45.00,69.00,300,190,'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1583337130417-3346a1be7dee?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `type`, `description`, `price`, `original_price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`) VALUES (1020,103,4,'宠物航空运输箱',2,'加固卡扣，通风透气，符合托运标准',129.00,179.00,150,70,'https://images.unsplash.com/photo-1518791841217-8f162f1e1131?auto=format&fit=crop&w=400&q=80','[\"https://images.unsplash.com/photo-1518791841217-8f162f1e1131?auto=format&fit=crop&w=400&q=80\"]',1,'2026-06-24 18:04:57','2026-06-24 18:04:57',0);
/*!40000 ALTER TABLE `product` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `product_sku` WRITE;
/*!40000 ALTER TABLE `product_sku` DISABLE KEYS */;
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5001,1007,'规格:2kg',89.00,200,'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5002,1007,'规格:5kg',199.00,150,'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5003,1007,'规格:10kg',369.00,100,'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5004,1008,'规格:1.8kg',158.00,120,'https://images.unsplash.com/photo-1623341214825-9f4f963727da?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5005,1008,'规格:5.4kg',398.00,80,'https://images.unsplash.com/photo-1623341214825-9f4f963727da?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5006,1015,'款式:小黄鸭',15.90,300,'https://images.unsplash.com/photo-1572085313466-6710de8d7ba3?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5007,1015,'款式:小熊',15.90,250,'https://images.unsplash.com/photo-1572085313466-6710de8d7ba3?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5008,1015,'款式:小象',17.90,200,'https://images.unsplash.com/photo-1572085313466-6710de8d7ba3?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5009,1016,'尺寸:S(5kg内)',69.00,120,'https://images.unsplash.com/photo-1541781774459-bb2af2f05b55?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5010,1016,'尺寸:M(10kg内)',99.00,90,'https://images.unsplash.com/photo-1541781774459-bb2af2f05b55?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5011,1016,'尺寸:L(20kg内)',139.00,60,'https://images.unsplash.com/photo-1541781774459-bb2af2f05b55?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5012,1017,'颜色:红色',39.00,150,'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5013,1017,'颜色:蓝色',39.00,140,'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5014,1017,'颜色:黑色',39.00,160,'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5015,1020,'尺寸:S',129.00,70,'https://images.unsplash.com/photo-1518791841217-8f162f1e1131?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5016,1020,'尺寸:M',169.00,50,'https://images.unsplash.com/photo-1518791841217-8f162f1e1131?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
INSERT INTO `product_sku` (`id`, `product_id`, `spec_name`, `price`, `stock`, `image`, `create_time`, `update_time`, `deleted`) VALUES (5017,1020,'尺寸:L',229.00,30,'https://images.unsplash.com/photo-1518791841217-8f162f1e1131?auto=format&fit=crop&w=400&q=80','2026-06-24 18:04:57','2026-06-24 18:04:57',0);
/*!40000 ALTER TABLE `product_sku` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

