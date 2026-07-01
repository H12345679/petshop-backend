package com.petshop.recommend.controller;

import com.petshop.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@RestController
@RequestMapping("/api/recommend/test")
public class MockDataController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.petshop.recommend.service.CollaborativeFilteringService cfService;

    @GetMapping("/mock-cf-data")
    @Transactional
    public Result<String> mockCfData() {
        Random random = new Random();
        
        // 1. 准备商品库 (分类)
        // 猫相关商品: 1008(猫粮), 1009(鸡肉冻干), 1012(逗猫棒), 1014(猫抓板), 1020(无谷猫粮), 1022(猫砂盆), 1027(猫薄荷鱼), 1031(无谷猫粮2), 1032(鸡肉冻干2), 1033(猫抓板2)
        List<Long> catItems = Arrays.asList(1008L, 1009L, 1012L, 1014L, 1020L, 1022L, 1027L, 1031L, 1032L, 1033L);
        // 狗相关商品: 1007(狗粮), 1011(咬胶), 1013(飞盘), 1021(小熊玩具), 1023(美毛狗粮), 1024(漏食球), 1025(车载座椅), 1026(双拼犬粮), 1029(狗粮2), 1030(飞盘2), 1034(咬胶2)
        List<Long> dogItems = Arrays.asList(1007L, 1011L, 1013L, 1021L, 1023L, 1024L, 1025L, 1026L, 1029L, 1030L, 1034L);

        // 2. 清理旧 Mock 数据
        jdbcTemplate.update("DELETE FROM user_item_score WHERE user_id >= 1000");
        jdbcTemplate.update("DELETE FROM user WHERE id >= 1000");

        // 3. 构造 20 个爱猫派 (User 1000~1019) 和 20 个爱狗派 (User 1020~1039)
        for (int i = 0; i < 40; i++) {
            long userId = 1000 + i;
            String username = "mock_user_" + userId;
            String nickname = (i < 20) ? "猫奴_" + i : "汪星人_" + i;
            
            // 插入用户
            jdbcTemplate.update(
                "INSERT INTO user (id, username, password, nickname, role, status, create_time, update_time, deleted) VALUES (?, ?, '123456', ?, 'USER', 1, NOW(), NOW(), 0)",
                userId, username, nickname
            );

            // 分配商品和评分
            boolean isCatLover = i < 20;
            List<Long> preferredItems = new ArrayList<>(isCatLover ? catItems : dogItems);
            List<Long> otherItems = new ArrayList<>(isCatLover ? dogItems : catItems);
            
            Collections.shuffle(preferredItems);
            Collections.shuffle(otherItems);

            // 挑 4-7 个偏好商品，给高分(3-5)
            int prefCount = 4 + random.nextInt(4);
            for (int j = 0; j < prefCount && j < preferredItems.size(); j++) {
                double score = 3.0 + random.nextInt(3); // 3, 4, 5
                jdbcTemplate.update(
                    "INSERT INTO user_item_score (id, user_id, product_id, score, update_time) VALUES (?, ?, ?, ?, NOW())",
                    Long.valueOf(userId + "00" + j), userId, preferredItems.get(j), score
                );
            }

            // 挑 0-2 个非偏好商品，给低分(1-2)
            int otherCount = random.nextInt(3);
            for (int j = 0; j < otherCount && j < otherItems.size(); j++) {
                double score = 1.0 + random.nextInt(2); // 1, 2
                jdbcTemplate.update(
                    "INSERT INTO user_item_score (id, user_id, product_id, score, update_time) VALUES (?, ?, ?, ?, NOW())",
                    Long.valueOf(userId + "99" + j), userId, otherItems.get(j), score
                );
            }
        }

        return Result.success("成功生成 40 个虚拟买家及对应的 CF 评分矩阵数据！");
    }

    @GetMapping("/run-cf")
    public Result<String> runCf() {
        cfService.runCollaborativeFilteringBatch();
        return Result.success("协同过滤跑批计算完成！");
    }
}
