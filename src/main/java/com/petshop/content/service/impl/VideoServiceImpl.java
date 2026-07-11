package com.petshop.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.common.BusinessException;
import com.petshop.common.PageResult;
import com.petshop.content.dto.VideoCreateDTO;
import com.petshop.content.dto.VideoPageQuery;
import com.petshop.content.entity.Video;
import com.petshop.content.mapper.VideoMapper;
import com.petshop.content.service.VideoService;
import com.petshop.content.vo.VideoDetailVO;
import com.petshop.product.entity.Product;
import com.petshop.product.service.ProductService;
import com.petshop.security.OwnershipChecker;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.petshop.content.entity.VideoES;
import com.petshop.content.repository.VideoESRepository;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频模块 Service 实现（E 模块 - E2）
 */
@Slf4j
@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    @Autowired
    private ProductService productService;

    @Autowired
    private OwnershipChecker ownershipChecker;

    @Autowired
    private VideoESRepository videoESRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    private VideoES mapToES(Video video) {
        if (video == null) return null;
        VideoES es = new VideoES();
        es.setId(video.getId());
        es.setTitle(video.getTitle());
        es.setCover(video.getCover());
        es.setDescription(video.getDescription());
        es.setProductId(video.getProductId());
        es.setShopId(video.getShopId());
        es.setViews(video.getViews());
        es.setStatus(video.getStatus());
        if (video.getCreateTime() != null) {
            es.setCreateTime(java.util.Date.from(video.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        }
        return es;
    }

    private void syncToEsAfterCommit(Long videoId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        Video dbVideo = getById(videoId);
                        if (dbVideo != null) {
                            videoESRepository.save(mapToES(dbVideo));
                        }
                    } catch (Exception e) {
                        log.error("同步新建/修改视频(ID:" + videoId + ")到 ES 失败", e);
                    }
                }
            });
        }
    }

    private void deleteFromEsAfterCommit(Long videoId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        videoESRepository.deleteById(videoId);
                    } catch (Exception e) {
                        log.error("从 ES 删除视频(ID:" + videoId + ")失败", e);
                    }
                }
            });
        }
    }

    @Override
    public long syncAllToES() {
        elasticsearchOperations.indexOps(IndexCoordinates.of("video")).exists();
        long totalSynced = 0;
        int current = 1;
        int size = 500;
        while (true) {
            Page<Video> page = this.page(new Page<>(current, size));
            List<Video> records = page.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            List<VideoES> esList = new ArrayList<>();
            for (Video v : records) {
                esList.add(mapToES(v));
            }
            if (!esList.isEmpty()) {
                videoESRepository.saveAll(esList);
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
    public Video createVideo(VideoCreateDTO dto, Long loginUserId, String role) {
        // MERCHANT 创建视频时，校验 shopId 是否属于本人名下
        ownershipChecker.assertShopOwned(dto.getShopId());
        Video video = new Video();
        video.setTitle(dto.getTitle());
        video.setCover(dto.getCover());
        video.setUrl(dto.getUrl());
        video.setDescription(dto.getDescription());
        video.setProductId(dto.getProductId() != null ? dto.getProductId() : 0L);
        video.setShopId(dto.getShopId());
        video.setViews(0);
        // 如果是管理员，默认上架(1)；如果是商家或普通用户，强制待审核(2)
        if ("ADMIN".equals(role)) {
            video.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        } else {
            video.setStatus(2);
        }
        save(video);
        
        syncToEsAfterCommit(video.getId());
        
        return video;
    }

    /**
     * 获取公开的视频分页列表（供小程序/前台用户浏览时使用）
     * 
     * @param query 包含了各种过滤条件的查询参数对象
     * @return 包含视频列表和分页信息的结果集
     */
    public PageResult<Video> pageVideos(VideoPageQuery query) {
        org.springframework.data.elasticsearch.core.query.Criteria criteria = new org.springframework.data.elasticsearch.core.query.Criteria();

        criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("status").is(1));

        if (StringUtils.hasText(query.getTitle())) {
            criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("title").matches(query.getTitle())
                    .or(new org.springframework.data.elasticsearch.core.query.Criteria("description").matches(query.getTitle())));
        }

        if (query.getProductId() != null && query.getProductId() > 0) {
            criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("productId").is(query.getProductId()));
        }

        if (query.getShopId() != null) {
            criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("shopId").is(query.getShopId()));
        }

        if (query.getProductCategoryId() != null && query.getProductCategoryId() > 0) {
            List<Product> products = productService.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                    .eq(Product::getCategoryId, query.getProductCategoryId()));
            if (!products.isEmpty()) {
                List<Long> productIds = products.stream().map(Product::getId).collect(java.util.stream.Collectors.toList());
                criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("productId").in(productIds));
            } else {
                return new PageResult<>();
            }
        }

        org.springframework.data.elasticsearch.core.query.CriteriaQuery cq = new org.springframework.data.elasticsearch.core.query.CriteriaQuery(criteria);
        cq.addSort(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createTime"));
        cq.setPageable(org.springframework.data.domain.PageRequest.of((int)(query.getCurrent() - 1), (int)query.getSize()));

        org.springframework.data.elasticsearch.core.SearchHits<VideoES> hits = elasticsearchOperations.search(cq, VideoES.class);
        
        List<Video> resultList = new ArrayList<>();
        if (hits.getTotalHits() > 0) {
            List<Long> idList = hits.getSearchHits().stream()
                    .map(h -> h.getContent().getId())
                    .collect(java.util.stream.Collectors.toList());
            List<Video> dbVideos = this.listByIds(idList);
            java.util.Map<Long, Video> map = dbVideos.stream().collect(java.util.stream.Collectors.toMap(Video::getId, v -> v));
            for (Long id : idList) {
                if (map.containsKey(id)) {
                    resultList.add(map.get(id));
                }
            }
        }

        PageResult<Video> pageResult = new PageResult<>();
        pageResult.setCurrent((long) query.getCurrent());
        pageResult.setSize((long) query.getSize());
        pageResult.setTotal(hits.getTotalHits());
        pageResult.setRecords(resultList);
        return pageResult;
    }

    @Override
    public PageResult<Video> manageVideos(VideoPageQuery query) {
        org.springframework.data.elasticsearch.core.query.Criteria criteria = new org.springframework.data.elasticsearch.core.query.Criteria();

        if (StringUtils.hasText(query.getTitle())) {
            criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("title").matches(query.getTitle())
                    .or(new org.springframework.data.elasticsearch.core.query.Criteria("description").matches(query.getTitle())));
        }

        if (query.getProductId() != null && query.getProductId() > 0) {
            criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("productId").is(query.getProductId()));
        }

        if (query.getShopId() != null) {
            criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("shopId").is(query.getShopId()));
        }

        if (query.getStatus() != null) {
            criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("status").is(query.getStatus()));
        }

        if (query.getProductCategoryId() != null && query.getProductCategoryId() > 0) {
            List<Product> products = productService.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                    .eq(Product::getCategoryId, query.getProductCategoryId()));
            if (!products.isEmpty()) {
                List<Long> productIds = products.stream().map(Product::getId).collect(java.util.stream.Collectors.toList());
                criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("productId").in(productIds));
            } else {
                return new PageResult<>();
            }
        }

        List<Long> shopIds = ownershipChecker.myShopIds();
        if (shopIds != null) {
            if (shopIds.isEmpty()) {
                return new PageResult<>();
            } else {
                criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("shopId").in(shopIds));
            }
        }

        org.springframework.data.elasticsearch.core.query.CriteriaQuery cq = new org.springframework.data.elasticsearch.core.query.CriteriaQuery(criteria);
        cq.addSort(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createTime"));
        cq.setPageable(org.springframework.data.domain.PageRequest.of((int)(query.getCurrent() - 1), (int)query.getSize()));

        org.springframework.data.elasticsearch.core.SearchHits<VideoES> hits = elasticsearchOperations.search(cq, VideoES.class);
        
        List<Video> resultList = new ArrayList<>();
        if (hits.getTotalHits() > 0) {
            List<Long> idList = hits.getSearchHits().stream()
                    .map(h -> h.getContent().getId())
                    .collect(java.util.stream.Collectors.toList());
            List<Video> dbVideos = this.listByIds(idList);
            java.util.Map<Long, Video> map = dbVideos.stream().collect(java.util.stream.Collectors.toMap(Video::getId, v -> v));
            for (Long id : idList) {
                if (map.containsKey(id)) {
                    resultList.add(map.get(id));
                }
            }
        }

        PageResult<Video> pageResult = new PageResult<>();
        pageResult.setCurrent((long) query.getCurrent());
        pageResult.setSize((long) query.getSize());
        pageResult.setTotal(hits.getTotalHits());
        pageResult.setRecords(resultList);
        return pageResult;
    }

    @Override
    public VideoDetailVO getVideoAndIncrViews(Long id) {
        Video video = getById(id);
        if (video == null) {
            throw new BusinessException(404, "视频不存在：" + id);
        }
        // 播放量 +1（简化设计：直接 UPDATE，高并发场景可改成 Redis 计数 + 定时同步）
        Video update = new Video();
        update.setId(id);
        update.setViews(video.getViews() + 1);
        updateById(update);
        video.setViews(video.getViews() + 1);
        
        syncToEsAfterCommit(id);

        // 组装 VO
        VideoDetailVO vo = new VideoDetailVO();
        BeanUtils.copyProperties(video, vo);

        // 关联商品信息：productId > 0 时查询商品基本信息，供前端"可跳商品"展示
        if (video.getProductId() != null && video.getProductId() > 0) {
            Product product = productService.getById(video.getProductId());
            if (product != null) {
                vo.setProductName(product.getName());
                vo.setProductMainImage(product.getMainImage());
                vo.setProductPrice(product.getPrice());
                vo.setProductStatus(product.getStatus());
            }
        }
        return vo;
    }

    @Override
    public void updateVideo(Long id, VideoCreateDTO dto, Long loginUserId, String role) {
        // 1. 先去数据库里查出这个视频原来的老数据
        Video existing = getById(id);
        if (existing == null) {
            throw new BusinessException(404, "视频不存在：" + id);
        }

        // 2. 权限校验：如果是商家（MERCHANT）来修改，必须得确保这个视频是他自己店铺里的
        ownershipChecker.assertShopOwned(existing.getShopId());

        // 3. 准备一个干净的新对象，只装载前端传过来（需要修改）的字段
        Video update = new Video();
        update.setId(id);

        // -- 逐个判断前端有没有传值过来。传了就更新，没传就保持原样（不覆盖原有数据） --
        if (StringUtils.hasText(dto.getTitle())) {
            update.setTitle(dto.getTitle());
        }
        if (StringUtils.hasText(dto.getCover())) {
            update.setCover(dto.getCover());
        }
        if (StringUtils.hasText(dto.getUrl())) {
            update.setUrl(dto.getUrl());
        }
        if (StringUtils.hasText(dto.getDescription())) {
            update.setDescription(dto.getDescription());
        }
        if (dto.getProductId() != null) {
            update.setProductId(dto.getProductId());
        }
        
        // 4. 审核状态处理
        if ("ADMIN".equals(role)) {
            // 只有超管有权力直接指定状态（比如前端传 1 就是上架，传 0 就是下架）
            if (dto.getStatus() != null) {
                update.setStatus(dto.getStatus());
            }
        } else {
            // 普通商家只要修改了视频的标题、封面、内容等信息，为了安全，系统强制将其打回“待审核(2)”状态
            update.setStatus(2);
        }
        
        // 5. 将这些装载好的新字段，更新到数据库里
        updateById(update);
        
        syncToEsAfterCommit(id);
    }

    @Override
    public void deleteVideo(Long id, Long loginUserId, String role) {
        Video existing = getById(id);
        if (existing == null) {
            throw new BusinessException(404, "视频不存在：" + id);
        }
        // MERCHANT 只能删本人店铺的视频，通过 shop.owner_id 校验
        ownershipChecker.assertShopOwned(existing.getShopId());
        removeById(id);
        deleteFromEsAfterCommit(id);
    }

}
