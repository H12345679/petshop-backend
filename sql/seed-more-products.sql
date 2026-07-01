SET NAMES utf8mb4;
USE petshop;

-- ---------- 商家用户 (5, 6, 7) ----------
INSERT IGNORE INTO user (id, username, password, nickname, role, member_level_id, status, create_time, update_time, deleted) VALUES
 (5, 'shop5', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '胖虎宠物', 'MERCHANT', 0, 1, NOW(), NOW(), 0),
 (6, 'shop6', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '金牌宠粮', 'MERCHANT', 0, 1, NOW(), NOW(), 0),
 (7, 'shop7', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '潮宠装备', 'MERCHANT', 0, 1, NOW(), NOW(), 0);

-- ---------- 商店 (104, 105, 106) ----------
INSERT IGNORE INTO shop (id, name, description, owner_id, status, create_time, update_time, deleted) VALUES
 (104, '胖虎宠物超市', '汇聚全球好物，专注宠物健康', 5, 1, NOW(), NOW(), 0),
 (105, '金牌宠粮专卖', '让每一口都放心，严选宠物主粮', 6, 1, NOW(), NOW(), 0),
 (106, '潮宠装备库', '好玩好用的宠物周边大集合', 7, 1, NOW(), NOW(), 0);

-- ---------- 商品 (分类 2食品, 3玩具, 4用品) ----------
INSERT IGNORE INTO product (id, shop_id, category_id, name, type, description, price, original_price, stock, sales, main_image, images, status, create_time, update_time, deleted) VALUES
 -- 胖虎宠物超市 (104)
 (1020, 104, 2, '无谷鸡肉全阶段猫粮 2kg', 2, '高蛋白无谷物，适合全阶段猫咪食用', 129.00, 159.00, 300, 120, 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0),
 (1021, 104, 3, '发声毛绒小熊玩具', 2, '内置发声气囊，耐咬材质，狗狗最爱', 25.00, 35.00, 500, 80, 'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0),
 (1022, 104, 4, '全封闭式防臭猫砂盆', 2, '超大空间，带落砂踏板，有效隔绝异味', 89.00, 129.00, 150, 45, 'https://images.unsplash.com/photo-1623387641168-d9803ddd3f35?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0),
 (1029, 104, 2, '皇家成犬全价狗粮 (胖虎宠物超市)', 2, '中型成犬粮，含益生元呵护肠道，多规格可选', 89.00, 119.00, 450, 260, 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0),
 (1030, 104, 3, '宠物耐咬飞盘 (胖虎宠物超市)', 2, '软硅胶材质不伤齿，浮水设计户外互动', 19.90, 29.90, 400, 150, 'https://images.unsplash.com/photo-1602491453631-e2a5ad90a131?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0),

 -- 金牌宠粮专卖 (105)
 (1023, 105, 2, '深海鱼油美毛狗粮 5kg', 2, '添加三文鱼油，亮泽毛发，适合中大型犬', 239.00, 289.00, 200, 210, 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0),
 (1024, 105, 3, '自动漏食不倒翁', 2, '边玩边吃，缓解焦虑，提升智力', 39.00, 59.00, 400, 160, 'https://images.unsplash.com/photo-1541781774459-bb2af2f05b55?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0),
 (1025, 105, 4, '宠物车载安全座椅', 2, '防水防滑，保障出行安全，中小型犬适用', 158.00, 198.00, 100, 30, 'https://images.unsplash.com/photo-1517849845537-4d257902454a?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0),
 (1031, 105, 2, '渴望六种鱼无谷猫粮 (金牌宠粮专卖)', 2, '85%动物原料，深海鱼配方，毛亮少泪痕', 158.00, 199.00, 200, 130, 'https://images.unsplash.com/photo-1623341214825-9f4f963727da?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0),
 (1032, 105, 2, '鸡肉冻干零食 100g (金牌宠粮专卖)', 2, '单一鸡胸肉冻干，高蛋白无添加，猫狗通用训练奖励', 39.90, 49.90, 500, 320, 'https://images.unsplash.com/photo-1601001815894-ea460c0cb3cc?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0),

 -- 潮宠装备库 (106)
 (1026, 106, 2, '主食冻干双拼犬粮', 2, '肉松冻干+高品质干粮，挑食克星', 188.00, 228.00, 250, 330, 'https://images.unsplash.com/photo-1601001815894-ea460c0cb3cc?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0),
 (1027, 106, 3, '猫薄荷毛绒仿真鱼', 2, '逼真造型，内含浓郁猫薄荷，让猫咪欲罢不能', 15.00, 25.00, 800, 540, 'https://images.unsplash.com/photo-1548199973-03cce0bbc87b?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0),
 (1028, 106, 4, '智能恒温宠物饮水机', 2, '多重过滤，APP温控，让爱宠爱上喝水', 199.00, 299.00, 120, 90, 'https://images.unsplash.com/photo-1525253086316-d0c936c814f8?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0),
 (1033, 106, 3, '瓦楞纸猫抓板 (潮宠装备库)', 2, '加厚双面可用，耐抓护沙发，送猫薄荷', 29.90, 39.90, 350, 270, 'https://images.unsplash.com/photo-1520315342629-6ea920342047?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0),
 (1034, 106, 3, '磨牙洁齿狗咬胶 (潮宠装备库)', 2, '天然马铃薯淀粉，清新口气、减少牙结石', 25.00, 35.00, 600, 210, 'https://images.unsplash.com/photo-1629851608405-f9e4215904fc?auto=format&fit=crop&w=400&q=80', '[]', 1, NOW(), NOW(), 0);

-- ---------- 关联商品标签 (Product_Tag) ----------
INSERT IGNORE INTO product_tag (product_id, tag_id) VALUES
 -- 1020 无谷鸡肉全阶段猫粮 2kg (猫咪2, 无谷14, 主食11)
 (1020, 2), (1020, 14), (1020, 11),
 -- 1021 发声毛绒小熊玩具 (狗狗1, 玩具13, 互动17)
 (1021, 1), (1021, 13), (1021, 17),
 -- 1022 全封闭式防臭猫砂盆 (猫咪2)
 (1022, 2),
 -- 1023 深海鱼油美毛狗粮 (狗狗1, 成年8, 主食11)
 (1023, 1), (1023, 8), (1023, 11),
 -- 1024 自动漏食不倒翁 (猫咪2, 狗狗1, 玩具13, 互动17)
 (1024, 2), (1024, 1), (1024, 13), (1024, 17),
 -- 1025 宠物车载安全座椅 (狗狗1, 户外18)
 (1025, 1), (1025, 18),
 -- 1026 主食冻干双拼犬粮 (狗狗1, 主食11)
 (1026, 1), (1026, 11),
 -- 1027 猫薄荷毛绒仿真鱼 (猫咪2, 玩具13, 互动17)
 (1027, 2), (1027, 13), (1027, 17),
 -- 1028 智能恒温宠物饮水机 (猫咪2, 狗狗1)
 (1028, 2), (1028, 1),
 -- 1029 皇家成犬全价狗粮 (胖虎宠物超市) (狗狗1, 成年8, 主食11)
 (1029, 1), (1029, 8), (1029, 11),
 -- 1030 宠物耐咬飞盘 (胖虎宠物超市) (狗狗1, 玩具13, 户外18, 互动17)
 (1030, 1), (1030, 13), (1030, 18), (1030, 17),
 -- 1031 渴望六种鱼无谷猫粮 (金牌宠粮专卖) (猫咪2, 无谷14, 主食11)
 (1031, 2), (1031, 14), (1031, 11),
 -- 1032 鸡肉冻干零食 (金牌宠粮专卖) (零食12, 鸡肉15)
 (1032, 12), (1032, 15),
 -- 1033 瓦楞纸猫抓板 (潮宠装备库) (猫咪2, 玩具13)
 (1033, 2), (1033, 13),
 -- 1034 磨牙洁齿狗咬胶 (潮宠装备库) (狗狗1, 零食12, 磨牙16)
 (1034, 1), (1034, 12), (1034, 16);

-- ---------- 商品 SKU ----------
INSERT IGNORE INTO product_sku (id, product_id, spec_name, price, stock, image, create_time, update_time, deleted) VALUES
 (50291, 1029, '规格:2kg',   89.00,  200, 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80', NOW(), NOW(), 0),
 (50292, 1029, '规格:5kg',   199.00, 150, 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80', NOW(), NOW(), 0),
 (50293, 1029, '规格:10kg',  369.00, 100, 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?auto=format&fit=crop&w=400&q=80', NOW(), NOW(), 0),
 (50311, 1031, '规格:1.8kg', 158.00, 120, 'https://images.unsplash.com/photo-1623341214825-9f4f963727da?auto=format&fit=crop&w=400&q=80', NOW(), NOW(), 0),
 (50312, 1031, '规格:5.4kg', 398.00, 80,  'https://images.unsplash.com/photo-1623341214825-9f4f963727da?auto=format&fit=crop&w=400&q=80', NOW(), NOW(), 0);
