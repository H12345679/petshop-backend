package com.petshop.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petshop.product.entity.Product;
import com.petshop.product.entity.Tag;
import com.petshop.product.mapper.ProductMapper;
import com.petshop.product.mapper.TagMapper;
import com.petshop.recommend.service.RecommendRankService;
import com.petshop.user.entity.User;
import com.petshop.user.mapper.UserMapper;
import com.petshop.user.service.UserPetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 多路召回 + 融合排序实现。
 * <p>
 * 四路召回：①CF(recommend_result) ②标签画像(Redis ZSET) ③宠物档案标签 ④人群热度
 * （养同类宠物的用户加购/购买热榜；无档案退化为同性别人群）。
 * 融合打分后做物种冲突过滤、近购惩罚、类目打散，并给出推荐理由。
 */
@Slf4j
@Service
public class RecommendRankServiceImpl implements RecommendRankService {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private TagMapper tagMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserPetService userPetService;

    // 融合权重（对应《推荐系统深化方案》§4，P0 先实现四项）
    private static final double W_CF = 0.40;
    private static final double W_TAG = 0.25;
    private static final double W_PET = 0.20;
    private static final double W_CROWD = 0.15;
    /** 近 30 天购买过的商品降权 */
    private static final double PENALTY_RECENT_BUY = 0.30;
    /** 同类目最多展示件数（打散） */
    private static final int MAX_PER_CATEGORY = 2;
    /** 物种标签名 → user_pet.species 编码 */
    private static final Map<String, Integer> SPECIES_TAG = new LinkedHashMap<>();
    static {
        SPECIES_TAG.put("猫咪", 1);
        SPECIES_TAG.put("狗狗", 2);
        SPECIES_TAG.put("兔子", 3);
        SPECIES_TAG.put("鸟类", 4);
    }

    @Override
    public List<Product> rankForUser(Long userId, int n) {
        if (userId == null || n <= 0) return new ArrayList<>();
        int candN = n * 3;

        // ===== 召回1：协同过滤（行为） =====
        Map<Long, Double> cfScore = recallCf(userId, candN);
        // ===== 召回2：标签画像（行为，带衰减后的 Redis 权重） =====
        Map<Long, Double> profileTagWeight = loadProfileTagWeights(userId); // tagId -> 归一化权重
        // ===== 召回3：宠物档案标签（个人信息） =====
        List<String> petTagNames = userPetService.petTagNames(userId);
        Set<Long> petTagIds = tagIdsByNames(petTagNames);
        List<Integer> mySpecies = userPetService.petSpecies(userId);
        // ===== 召回4：人群热度（个人信息：养宠人群 > 同性别人群） =====
        Map<Long, Double> crowdScore = recallCrowd(userId, mySpecies, candN);

        // 候选池 = 各路并集
        Set<Long> candidateIds = new LinkedHashSet<>(cfScore.keySet());
        if (!profileTagWeight.isEmpty()) {
            candidateIds.addAll(productIdsByTagIds(profileTagWeight.keySet(), candN));
        }
        if (!petTagIds.isEmpty()) {
            candidateIds.addAll(productIdsByTagIds(petTagIds, candN));
        }
        candidateIds.addAll(crowdScore.keySet());
        if (candidateIds.isEmpty()) return new ArrayList<>();

        // 批量取商品（上架）与商品标签
        Map<Long, Product> products = loadActiveProducts(candidateIds);
        if (products.isEmpty()) return new ArrayList<>();
        Map<Long, Set<Long>> productTags = loadProductTags(products.keySet());
        Set<Long> recentBought = loadRecentBought(userId);

        // ===== 融合打分 =====
        double cfMax = maxValue(cfScore);
        double crowdMax = maxValue(crowdScore);
        List<Object[]> scored = new ArrayList<>(); // [productId, finalScore, reason]
        for (Long pid : products.keySet()) {
            Set<Long> tags = productTags.getOrDefault(pid, Collections.emptySet());

            // 物种冲突硬过滤：有档案时，带物种标签且与我的宠物全不匹配的商品直接不推
            if (!mySpecies.isEmpty() && speciesConflict(tags, mySpecies)) continue;

            double cf = cfMax > 0 ? cfScore.getOrDefault(pid, 0.0) / cfMax : 0.0;

            double tagMatch = 0.0;
            for (Long t : tags) {
                Double w = profileTagWeight.get(t);
                if (w != null) tagMatch += w;
            }
            if (tagMatch > 1.0) tagMatch = 1.0;

            double pet;
            if (petTagIds.isEmpty()) {
                pet = 0.5; // 无档案取中性值，不奖不罚
            } else {
                int matched = 0;
                for (Long t : tags) if (petTagIds.contains(t)) matched++;
                pet = (double) matched / petTagIds.size();
                if (pet > 1.0) pet = 1.0;
            }

            double crowd = crowdMax > 0 ? crowdScore.getOrDefault(pid, 0.0) / crowdMax : 0.0;

            double score = W_CF * cf + W_TAG * tagMatch + W_PET * pet + W_CROWD * crowd;
            if (recentBought.contains(pid)) score -= PENALTY_RECENT_BUY;
            if (score <= 0) continue;

            scored.add(new Object[]{pid, score, pickReason(cf, tagMatch, pet, crowd, petTagIds.isEmpty(), mySpecies)});
        }
        scored.sort((a, b) -> Double.compare((double) b[1], (double) a[1]));

        // ===== 重排：类目打散（同类目最多 MAX_PER_CATEGORY 个）=====
        List<Product> result = new ArrayList<>();
        Map<Long, Integer> catCount = new HashMap<>();
        for (Object[] s : scored) {
            if (result.size() >= n) break;
            Product p = products.get((Long) s[0]);
            Long cat = p.getCategoryId() != null ? p.getCategoryId() : -1L;
            int used = catCount.getOrDefault(cat, 0);
            if (used >= MAX_PER_CATEGORY) continue;
            catCount.put(cat, used + 1);
            p.setRecommendReason((String) s[2]);
            result.add(p);
        }
        // 打散后不足 n，把被打散规则跳过的高分商品补回来
        if (result.size() < n) {
            for (Object[] s : scored) {
                if (result.size() >= n) break;
                Product p = products.get((Long) s[0]);
                if (!result.contains(p)) {
                    p.setRecommendReason((String) s[2]);
                    result.add(p);
                }
            }
        }
        return result;
    }

    // ==================== 召回 ====================

    /** CF 离线结果召回：productId -> 原始得分 */
    private Map<Long, Double> recallCf(Long userId, int limit) {
        Map<Long, Double> map = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT product_id, score FROM recommend_result WHERE user_id = ? ORDER BY score DESC LIMIT ?",
                    userId, limit);
            for (Map<String, Object> r : rows) {
                map.put(((Number) r.get("product_id")).longValue(), ((Number) r.get("score")).doubleValue());
            }
        } catch (Exception e) {
            log.warn("CF 召回失败: {}", e.getMessage());
        }
        return map;
    }

    /** Redis 标签画像 Top5：tagId -> 权重（按最大值归一化到 0~1） */
    private Map<Long, Double> loadProfileTagWeights(Long userId) {
        Map<Long, Double> map = new LinkedHashMap<>();
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                    .reverseRangeWithScores("user_profile:" + userId + ":tags", 0, 4);
            if (tuples == null || tuples.isEmpty()) return map;
            double max = 0;
            for (ZSetOperations.TypedTuple<String> t : tuples) {
                if (t.getScore() != null && t.getScore() > max) max = t.getScore();
            }
            if (max <= 0) return map;
            for (ZSetOperations.TypedTuple<String> t : tuples) {
                if (t.getValue() == null || t.getScore() == null) continue;
                try {
                    map.put(Long.parseLong(t.getValue()), t.getScore() / max);
                } catch (NumberFormatException ignored) { }
            }
        } catch (Exception e) {
            log.warn("标签画像读取失败: {}", e.getMessage());
        }
        return map;
    }

    /**
     * 人群热度召回：优先"养同类宠物的用户"的加购/购买热榜（个人信息深度参与）；
     * 无宠物档案时退化为"同性别用户"热榜。productId -> 人次
     */
    private Map<Long, Double> recallCrowd(Long userId, List<Integer> mySpecies, int limit) {
        Map<Long, Double> map = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows;
            if (!mySpecies.isEmpty()) {
                String in = joinInts(mySpecies);
                rows = jdbcTemplate.queryForList(
                        "SELECT ub.product_id, COUNT(DISTINCT ub.user_id) cnt FROM user_behavior ub " +
                        "JOIN user_pet up ON ub.user_id = up.user_id AND up.deleted = 0 " +
                        "WHERE up.species IN (" + in + ") AND ub.behavior_type IN (3,4) AND ub.deleted = 0 " +
                        "AND ub.user_id <> ? GROUP BY ub.product_id ORDER BY cnt DESC LIMIT ?",
                        userId, limit);
            } else {
                User me = userMapper.selectById(userId);
                Integer gender = me != null ? me.getGender() : null;
                if (gender == null || (gender != 1 && gender != 2)) return map;
                rows = jdbcTemplate.queryForList(
                        "SELECT ub.product_id, COUNT(DISTINCT ub.user_id) cnt FROM user_behavior ub " +
                        "JOIN `user` u ON ub.user_id = u.id AND u.deleted = 0 " +
                        "WHERE u.gender = ? AND ub.behavior_type IN (3,4) AND ub.deleted = 0 " +
                        "AND ub.user_id <> ? GROUP BY ub.product_id ORDER BY cnt DESC LIMIT ?",
                        gender, userId, limit);
            }
            for (Map<String, Object> r : rows) {
                map.put(((Number) r.get("product_id")).longValue(), ((Number) r.get("cnt")).doubleValue());
            }
        } catch (Exception e) {
            log.warn("人群热度召回失败: {}", e.getMessage());
        }
        return map;
    }

    // ==================== 数据加载 ====================

    private Set<Long> tagIdsByNames(List<String> names) {
        Set<Long> ids = new LinkedHashSet<>();
        if (names == null || names.isEmpty()) return ids;
        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>().in(Tag::getName, names));
        for (Tag t : tags) ids.add(t.getId());
        return ids;
    }

    private Set<Long> productIdsByTagIds(Collection<Long> tagIds, int limit) {
        Set<Long> ids = new LinkedHashSet<>();
        if (tagIds == null || tagIds.isEmpty()) return ids;
        try {
            List<Long> rows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT product_id FROM product_tag WHERE tag_id IN (" + joinLongs(tagIds) + ") LIMIT " + limit,
                    Long.class);
            ids.addAll(rows);
        } catch (Exception e) {
            log.warn("标签召回失败: {}", e.getMessage());
        }
        return ids;
    }

    /** 批量取上架商品 */
    private Map<Long, Product> loadActiveProducts(Collection<Long> ids) {
        Map<Long, Product> map = new LinkedHashMap<>();
        if (ids.isEmpty()) return map;
        List<Product> list = productMapper.selectBatchIds(ids);
        for (Product p : list) {
            if (p.getStatus() != null && p.getStatus() == 1) {
                map.put(p.getId(), p);
            }
        }
        return map;
    }

    /** 批量取候选商品的标签集合 */
    private Map<Long, Set<Long>> loadProductTags(Collection<Long> productIds) {
        Map<Long, Set<Long>> map = new HashMap<>();
        if (productIds.isEmpty()) return map;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT product_id, tag_id FROM product_tag WHERE product_id IN (" + joinLongs(productIds) + ")");
        for (Map<String, Object> r : rows) {
            map.computeIfAbsent(((Number) r.get("product_id")).longValue(), k -> new HashSet<>())
               .add(((Number) r.get("tag_id")).longValue());
        }
        return map;
    }

    /** 近 30 天已购商品（惩罚项，避免"买过还反复推"） */
    private Set<Long> loadRecentBought(Long userId) {
        Set<Long> set = new HashSet<>();
        try {
            List<Long> rows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT oi.product_id FROM order_item oi JOIN orders o ON oi.order_id = o.id " +
                    "WHERE o.user_id = ? AND o.status >= 1 AND o.create_time >= NOW() - INTERVAL 30 DAY",
                    Long.class, userId);
            set.addAll(rows);
        } catch (Exception e) {
            log.warn("近购查询失败: {}", e.getMessage());
        }
        return set;
    }

    // ==================== 规则 ====================

    /** 商品带物种标签且与用户宠物全不匹配 → 冲突（猫用户不出狗主粮） */
    private boolean speciesConflict(Set<Long> productTagIds, List<Integer> mySpecies) {
        boolean hasSpeciesTag = false;
        for (Map.Entry<String, Integer> e : SPECIES_TAG.entrySet()) {
            Long tagId = speciesTagId(e.getKey());
            if (tagId != null && productTagIds.contains(tagId)) {
                hasSpeciesTag = true;
                if (mySpecies.contains(e.getValue())) return false; // 命中我的宠物 → 不冲突
            }
        }
        return hasSpeciesTag; // 有物种标签但全未命中 → 冲突
    }

    /** 物种标签 id 缓存（字典极小，直接查一次缓存进内存） */
    private volatile Map<String, Long> speciesTagIdCache;

    private Long speciesTagId(String name) {
        if (speciesTagIdCache == null) {
            synchronized (this) {
                if (speciesTagIdCache == null) {
                    Map<String, Long> m = new HashMap<>();
                    List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>().in(Tag::getName, SPECIES_TAG.keySet()));
                    for (Tag t : tags) m.put(t.getName(), t.getId());
                    speciesTagIdCache = m;
                }
            }
        }
        return speciesTagIdCache.get(name);
    }

    /** 按四路贡献大小挑推荐理由（个人信息路优先展示，演示效果直观） */
    private String pickReason(double cf, double tag, double pet, double crowd,
                              boolean noPet, List<Integer> mySpecies) {
        double petContrib = noPet ? 0 : W_PET * pet;
        double tagContrib = W_TAG * tag;
        double cfContrib = W_CF * cf;
        double crowdContrib = W_CROWD * crowd;

        double max = Math.max(Math.max(petContrib, tagContrib), Math.max(cfContrib, crowdContrib));
        if (max <= 0) return "热销好物";
        if (max == petContrib) return "为你的" + speciesNick(mySpecies) + "挑选";
        if (max == tagContrib) return "根据你的浏览偏好";
        if (max == crowdContrib) return noPet ? "同好用户都在买" : "养" + speciesNick(mySpecies) + "的用户都在买";
        return "和你相似的用户也买了";
    }

    private String speciesNick(List<Integer> species) {
        if (species.size() == 1) {
            switch (species.get(0)) {
                case 1: return "猫咪";
                case 2: return "狗狗";
                case 3: return "兔子";
                case 4: return "鸟儿";
                default: return "爱宠";
            }
        }
        return "爱宠";
    }

    // ==================== 工具 ====================

    private double maxValue(Map<Long, Double> map) {
        double max = 0;
        for (Double v : map.values()) if (v != null && v > max) max = v;
        return max;
    }

    private String joinLongs(Collection<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    private String joinInts(Collection<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }
}
