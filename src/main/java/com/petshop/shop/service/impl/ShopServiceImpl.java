package com.petshop.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.common.BusinessException;
import com.petshop.common.PageResult;
import com.petshop.common.ResultCode;
import com.petshop.security.OwnershipChecker;
import com.petshop.security.UserContext;
import com.petshop.shop.entity.Shop;
import com.petshop.shop.mapper.ShopMapper;
import com.petshop.shop.service.ShopPageQuery;
import com.petshop.shop.service.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.petshop.shop.entity.ShopES;
import com.petshop.shop.repository.ShopESRepository;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.List;

/**
 * 商店 Service 实现。
 * <p>
 * 继承 ServiceImpl&lt;ShopMapper, Shop&gt; → 自动拥有 save/getById/updateById/removeById/page 等。
 * 本类只补「业务规则」：建店绑店主、改删校验归属、列表组合条件。
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {

    /** 商家数据归属校验工具（ADMIN 放行、MERCHANT 校验 owner_id）。 */
    @Autowired
    private OwnershipChecker ownershipChecker;

    @Autowired
    private ShopESRepository shopESRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    private ShopES mapToES(Shop shop) {
        if (shop == null) return null;
        ShopES es = new ShopES();
        es.setId(shop.getId());
        es.setName(shop.getName());
        es.setDescription(shop.getDescription());
        es.setOwnerId(shop.getOwnerId());
        es.setStatus(shop.getStatus());
        if (shop.getCreateTime() != null) {
            es.setCreateTime(java.util.Date.from(shop.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        }
        return es;
    }

    private void syncToEsAfterCommit(Long shopId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        Shop dbShop = getById(shopId);
                        if (dbShop != null) {
                            shopESRepository.save(mapToES(dbShop));
                        }
                    } catch (Exception e) {
                        log.error("同步新建/修改店铺(ID:" + shopId + ")到 ES 失败", e);
                    }
                }
            });
        }
    }

    private void deleteFromEsAfterCommit(Long shopId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        shopESRepository.deleteById(shopId);
                    } catch (Exception e) {
                        log.error("从 ES 删除店铺(ID:" + shopId + ")失败", e);
                    }
                }
            });
        }
    }

    @Override
    public long syncAllToES() {
        elasticsearchOperations.indexOps(IndexCoordinates.of("shop")).exists();
        long totalSynced = 0;
        int current = 1;
        int size = 500;
        while (true) {
            Page<Shop> page = this.page(new Page<>(current, size));
            List<Shop> records = page.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            List<ShopES> esList = new ArrayList<>();
            for (Shop s : records) {
                esList.add(mapToES(s));
            }
            if (!esList.isEmpty()) {
                shopESRepository.saveAll(esList);
                totalSynced += esList.size();
            }
            if (current >= page.getPages()) {
                break;
            }
            current++;
        }
        return totalSynced;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void createShop(Shop shop) {
        // 1) 商家建店：owner_id 强制为当前登录人，忽略前端越权传入；ADMIN 可代为指定 ownerId
        if (!ownershipChecker.isAdmin()) {
            shop.setOwnerId(UserContext.getUserId());
        }
        // 2) 默认值兜底：不传状态默认营业
        if (shop.getStatus() == null) {
            shop.setStatus(1);
        }
        // 3) 主键由雪花算法生成，防止前端塞 id 干扰
        shop.setId(null);
        // 4) save() 是 ServiceImpl 白送的：自动 INSERT，并回填生成的 id 到 shop 对象
        this.save(shop);
        
        // 5) 同步到 ES
        syncToEsAfterCommit(shop.getId());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void updateShop(Long id, Shop shop) {
        // 商家只能改自己的店；ADMIN 直接放行；店不存在抛 404，越权抛 403
        ownershipChecker.assertShopOwned(id);

        shop.setId(id);          // 用路径上的 id 作为 WHERE 条件，避免改错对象
        shop.setOwnerId(null);   // 不允许通过修改接口转移店主
        // updateById 默认只更新「非空字段」，所以这是局部更新（前端没传的字段不动）
        boolean ok = this.updateById(shop);
        if (!ok) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        
        // 同步到 ES
        syncToEsAfterCommit(id);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void deleteShop(Long id) {
        ownershipChecker.assertShopOwned(id);
        // removeById 因实体有 @TableLogic，实际执行 UPDATE ... SET deleted=1（逻辑删除）
        boolean ok = this.removeById(id);
        if (!ok) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        
        // 从 ES 删除
        deleteFromEsAfterCommit(id);
    }

    @Override
    public PageResult<Shop> pageShops(ShopPageQuery query) {
        org.springframework.data.elasticsearch.core.query.Criteria criteria = new org.springframework.data.elasticsearch.core.query.Criteria();

        // 1. 组合查询条件
        if (StringUtils.hasText(query.getName())) {
            criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("name").matches(query.getName())
                    .or(new org.springframework.data.elasticsearch.core.query.Criteria("description").matches(query.getName())));
        }
        if (query.getStatus() != null) {
            criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("status").is(query.getStatus()));
        }

        // 2. 商家归属过滤
        List<Long> shopIds = ownershipChecker.myShopIds();
        if (shopIds != null) {
            if (shopIds.isEmpty()) {
                return new PageResult<>(); // MERCHANT 无店铺 → 返回空
            } else {
                criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("id").in(shopIds));
            }
        } else if (query.getOwnerId() != null) {
            criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("ownerId").is(query.getOwnerId()));
        }

        org.springframework.data.elasticsearch.core.query.CriteriaQuery cq = new org.springframework.data.elasticsearch.core.query.CriteriaQuery(criteria);
        cq.addSort(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createTime"));
        cq.setPageable(org.springframework.data.domain.PageRequest.of((int)(query.getCurrent() - 1), (int)query.getSize()));

        org.springframework.data.elasticsearch.core.SearchHits<ShopES> hits = elasticsearchOperations.search(cq, ShopES.class);
        
        List<Shop> resultList = new ArrayList<>();
        if (hits.getTotalHits() > 0) {
            List<Long> idList = hits.getSearchHits().stream()
                    .map(h -> h.getContent().getId())
                    .collect(java.util.stream.Collectors.toList());
            List<Shop> dbShops = this.listByIds(idList);
            java.util.Map<Long, Shop> shopMap = dbShops.stream().collect(java.util.stream.Collectors.toMap(Shop::getId, s -> s));
            for (Long shopId : idList) {
                if (shopMap.containsKey(shopId)) {
                    resultList.add(shopMap.get(shopId));
                }
            }
        }

        PageResult<Shop> pageResult = new PageResult<>();
        pageResult.setCurrent((long) query.getCurrent());
        pageResult.setSize((long) query.getSize());
        pageResult.setTotal(hits.getTotalHits());
        pageResult.setRecords(resultList);
        return pageResult;
    }
}
